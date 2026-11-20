package me.stella.scan.model;

import java.util.ConcurrentModificationException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.LongSupplier;

/**
 * Thread-safe progress ledger shared by a running scan and its {@link ScanMonitor}.
 *
 * <p>The writer side (native adapters and {@code CapybaraNative} defaults, often on many worker threads at once) only
 * declares a {@link #total(long) total}, {@link #advance() advances} units of work and {@link #watch(Map) exposes}
 * its live output. The reader side samples immutable {@link Snapshot}s; all formatting happens on the observer.</p>
 */
public final class ScanProgress {

    public static final String PHASE_PREPARING = "Preparing";
    public static final String PHASE_SCANNING = "Scanning";
    public static final String PHASE_WRITING = "Writing report";

    private static final LongSupplier ZERO = () -> 0L;

    private final long startedNanos = System.nanoTime();
    private final AtomicLong total = new AtomicLong();
    private final LongAdder done = new LongAdder();
    private volatile String phase = PHASE_PREPARING;
    private volatile LongSupplier entries = ZERO;
    private volatile LongSupplier amount;
    private volatile long lastEntries;
    private volatile long lastAmount;

    /** A ledger nobody observes - for callers that do not care about progress. */
    public static ScanProgress detached() {
        return new ScanProgress();
    }

    /** Declares the number of work units (files, regions, listings...) this scan will process. */
    public ScanProgress total(long units) {
        total.set(Math.max(0L, units));
        return this;
    }

    /** Grows the declared total, for scans that discover work incrementally. */
    public ScanProgress expand(long units) {
        if (units > 0L) {
            total.addAndGet(units);
        }
        return this;
    }

    public void advance() {
        done.increment();
    }

    public void advance(long units) {
        if (units > 0L) {
            done.add(units);
        }
    }

    public ScanProgress phase(String phase) {
        this.phase = phase;
        return this;
    }

    public String phase() {
        return phase;
    }

    /** Counts the output live: entries = keys, amount = sum of values. The map must tolerate concurrent reads. */
    public ScanProgress watch(Map<?, ? extends Number> output) {
        return watch(output::size, () -> {
            long sum = 0L;
            for (Number value : output.values()) {
                sum += value.longValue();
            }
            return sum;
        });
    }

    /** Counts the output live through custom probes; {@code amount} may be {@code null} when meaningless. */
    public ScanProgress watch(LongSupplier entries, LongSupplier amount) {
        this.entries = entries == null ? ZERO : entries;
        this.amount = amount;
        return this;
    }

    public Snapshot snapshot() {
        long declared = total.get();
        long processed = done.sum();
        lastEntries = probe(entries, lastEntries);
        LongSupplier amountProbe = amount;
        long amountValue = -1L;
        if (amountProbe != null) {
            lastAmount = probe(amountProbe, lastAmount);
            amountValue = lastAmount;
        }
        return new Snapshot(phase, declared > 0L ? Math.min(processed, declared) : processed, declared,
                lastEntries, amountValue, System.nanoTime() - startedNanos);
    }

    private static long probe(LongSupplier probe, long fallback) {
        try {
            return probe.getAsLong();
        } catch (ConcurrentModificationException | NullPointerException racedWithWriter) {
            return fallback;
        }
    }

    /**
     * Immutable sample of a scan.
     *
     * @param amount summed output value, or {@code -1} when the scan does not track one
     */
    public record Snapshot(String phase, long done, long total, long entries, long amount, long elapsedNanos) {

        public boolean determinate() {
            return total > 0L;
        }

        public double ratio() {
            return total <= 0L ? 0D : Math.min(1D, (double) done / (double) total);
        }

        public boolean hasAmount() {
            return amount >= 0L;
        }

        /** Linear estimate of the remaining time, or {@code -1} when unknown. */
        public long etaNanos() {
            if (done <= 0L || total <= 0L || done >= total) {
                return -1L;
            }
            return (long) (elapsedNanos * ((double) (total - done) / (double) done));
        }
    }
}
