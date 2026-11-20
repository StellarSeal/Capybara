package me.stella.ui;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Thread-safe number / duration formatting for the UI. */
public final class Format {

    private Format() {
    }

    /** {@code 1234567 -> "1,234,567"}. */
    public static String count(long value) {
        return String.format(Locale.US, "%,d", value);
    }

    /** {@code 0.4213 -> "42.1%"}. */
    public static String percent(double ratio) {
        return String.format(Locale.ROOT, "%.1f%%", ratio * 100D);
    }

    /** {@code m:ss}, or {@code h:mm:ss} past an hour. */
    public static String clock(long nanos) {
        long seconds = TimeUnit.NANOSECONDS.toSeconds(Math.max(0L, nanos));
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long secs = seconds % 60L;
        return hours > 0L
                ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, secs)
                : String.format(Locale.ROOT, "%d:%02d", minutes, secs);
    }

    /** {@code 16.2s}, or {@link #clock(long)} past a minute. */
    public static String seconds(long nanos) {
        return nanos >= TimeUnit.MINUTES.toNanos(1L)
                ? clock(nanos)
                : String.format(Locale.ROOT, "%.1fs", nanos / 1_000_000_000D);
    }

    /** Short, single-line description of a failure. */
    public static String failure(Throwable failure) {
        if (failure == null) {
            return "unknown error";
        }
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return root.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }
}
