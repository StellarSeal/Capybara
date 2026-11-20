package me.stella.scan.model;

import java.util.ConcurrentModificationException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.LongSupplier;

/**
 * Thread-safe progress ledger shared by a running scan and its observer monitor.
 *
 * <p>Uses O(1) {@link LongAdder} counters for live match tracking so the monitor never iterates
 * large result maps on entity or main server threads.</p>
 */
public final class ScanProgress {

   public static final String PHASE_PREPARING = "Preparing";
   public static final String PHASE_SCANNING = "Scanning";
   public static final String PHASE_WRITING = "Writing report";

   private static final LongSupplier ZERO = () -> 0L;

   private final long startedNanos = System.nanoTime();
   private final AtomicLong total = new AtomicLong();
   private final LongAdder done = new LongAdder();
   private final LongAdder entries = new LongAdder();
   private final LongAdder amount = new LongAdder();
   private final LongAdder errors = new LongAdder();
   private final AtomicBoolean cancelled = new AtomicBoolean();

   private volatile String phase = PHASE_PREPARING;
   private volatile boolean atomicAmountEnabled;
   private volatile LongSupplier customEntries;
   private volatile LongSupplier customAmount;
   private volatile long lastEntries;
   private volatile long lastAmount;

   public static ScanProgress detached() {
      return new ScanProgress();
   }

   public ScanProgress total(long units) {
      total.set(Math.max(0L, units));
      return this;
   }

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

   /** Enables O(1) atomic match counters (entries + optional summed amount) for high-volume scans. */
   public ScanProgress trackAtomic(boolean trackAmount) {
      customEntries = null;
      customAmount = null;
      atomicAmountEnabled = trackAmount;
      return this;
   }

   public void recordMatch(long entryDelta, long amountDelta) {
      if (entryDelta != 0L) {
         entries.add(entryDelta);
      }
      if (amountDelta != 0L) {
         amount.add(amountDelta);
      }
   }

   public void recordError() {
      errors.increment();
   }

   public long errors() {
      return errors.sum();
   }

   public void cancel() {
      cancelled.set(true);
   }

   public boolean isCancelled() {
      return cancelled.get();
   }

   /** Legacy probe for small maps; prefer {@link #trackAtomic(boolean)} for large scans. */
   public ScanProgress watch(Map<?, ? extends Number> output) {
      return watch(output::size, () -> {
         long sum = 0L;
         for (Number value : output.values()) {
            sum += value.longValue();
         }
         return sum;
      });
   }

   public ScanProgress watch(LongSupplier entriesProbe, LongSupplier amountProbe) {
      this.customEntries = entriesProbe == null ? ZERO : entriesProbe;
      this.customAmount = amountProbe;
      return this;
   }

   public Snapshot snapshot() {
      long declared = total.get();
      long processed = done.sum();
      long entryCount;
      long amountValue;

      LongSupplier entriesProbe = customEntries;
      if (entriesProbe != null) {
         lastEntries = probe(entriesProbe, lastEntries);
         entryCount = lastEntries;
         LongSupplier amountProbe = customAmount;
         if (amountProbe != null) {
            lastAmount = probe(amountProbe, lastAmount);
            amountValue = lastAmount;
         } else {
            amountValue = -1L;
         }
      } else {
         entryCount = entries.sum();
         amountValue = atomicAmountEnabled ? amount.sum() : -1L;
      }

      return new Snapshot(
            phase,
            declared > 0L ? Math.min(processed, declared) : processed,
            declared,
            entryCount,
            amountValue,
            errors.sum(),
            System.nanoTime() - startedNanos);
   }

   private static long probe(LongSupplier probe, long fallback) {
      try {
         return probe.getAsLong();
      } catch (ConcurrentModificationException | NullPointerException racedWithWriter) {
         return fallback;
      }
   }

   public record Snapshot(
         String phase,
         long done,
         long total,
         long entries,
         long amount,
         long errors,
         long elapsedNanos) {

      public boolean determinate() {
         return total > 0L;
      }

      public double ratio() {
         return total <= 0L ? 0D : Math.min(1D, (double) done / (double) total);
      }

      public boolean hasAmount() {
         return amount >= 0L;
      }

      public long etaNanos() {
         if (done <= 0L || total <= 0L || done >= total) {
            return -1L;
         }
         return (long) (elapsedNanos * ((double) (total - done) / (double) done));
      }
   }
}
