package me.stella.scan.model;

import java.io.File;

/**
 * Immutable description of one scan: what it is called, what it looks for, how its work is measured and the work itself.
 *
 * @param title   human name, e.g. {@code "Inventory scan"}
 * @param subject what is being searched for, e.g. the held item's search id
 * @param unit    plural noun of a progress unit, e.g. {@code "players"} or {@code "regions"}
 * @param counter how the live output counter is labelled
 * @param work    the scan itself; returns the written report
 */
public record ScanJob(String title, String subject, String unit, Counter counter, Work work) {

    /** Labels for the live output counter ({@code entries} = output keys, {@code amount} = summed values). */
    public record Counter(String entries, String amount) {
        public static final Counter HOLDERS = new Counter("holders", "items");
        public static final Counter OWNERS = new Counter("owners", "items");
        public static final Counter CONTAINERS = new Counter("containers", "items");
        public static final Counter ITEM_TYPES = new Counter("item types", "items");
        public static final Counter MATCHES = new Counter("matches", null);
    }

    @FunctionalInterface
    public interface Work {
        File run(ScanProgress progress) throws Exception;

        /** Collects data while reporting progress, switches to the "writing" phase, then persists the report. */
        static <T> Work of(Collector<T> collector, Writer<T> writer) {
            return progress -> {
                T data = collector.collect(progress);
                progress.phase(ScanProgress.PHASE_WRITING);
                return writer.write(data);
            };
        }
    }

    @FunctionalInterface
    public interface Collector<T> {
        T collect(ScanProgress progress) throws Exception;
    }

    @FunctionalInterface
    public interface Writer<T> {
        File write(T data) throws Exception;
    }
}
