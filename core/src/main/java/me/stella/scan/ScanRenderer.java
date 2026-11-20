package me.stella.scan;

import me.stella.ui.Format;
import me.stella.ui.Theme;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/** Builds the Adventure components shown while a scan runs and once it settles. */
public final class ScanRenderer {

    private static final String[] SPINNER = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};
    private static final int BAR_CELLS = 30;
    private static final int SWEEP_WIDTH = 6;
    private static final String CELL = "|";
    private static final Component SEPARATOR = Component.text("  │  ", Theme.DIM);

    private ScanRenderer() {
    }

    /** Live action bar, e.g. {@code ⠹ Inventory scan  |||||||||||       42.1%  │  812/1,930 players  │  ✦ 14 holders · 1,204 items  │  ⏱ 0:12 ⇢ 0:16}. */
    public static Component progress(ScanSession session, ScanProgress.Snapshot snapshot, int frame) {
        ScanJob job = session.job();
        TextComponent.Builder line = Component.text()
                .append(Component.text(SPINNER[frame % SPINNER.length] + " ", Theme.ACCENT))
                .append(Component.text(job.title(), Theme.PRIMARY, TextDecoration.BOLD))
                .append(Component.text("  "));
        if (snapshot.determinate()) {
            line.append(bar(snapshot.ratio()))
                    .append(Component.text(" " + Format.percent(snapshot.ratio()), Theme.HIGHLIGHT))
                    .append(SEPARATOR)
                    .append(Component.text(Format.count(snapshot.done()), NamedTextColor.WHITE))
                    .append(Component.text("/" + Format.count(snapshot.total()) + " " + job.unit(), Theme.MUTED));
        } else {
            line.append(sweep(frame));
        }
        if (!ScanProgress.PHASE_SCANNING.equals(snapshot.phase())) {
            line.append(SEPARATOR).append(Component.text(snapshot.phase() + "…", Theme.MUTED, TextDecoration.ITALIC));
        }
        line.append(SEPARATOR).append(counter(job.counter(), snapshot))
                .append(SEPARATOR)
                .append(Component.text("⏱ " + Format.clock(snapshot.elapsedNanos()), Theme.MUTED));
        long eta = snapshot.etaNanos();
        if (eta >= 0L) {
            line.append(Component.text(" ⇢ " + Format.clock(eta), Theme.DIM));
        }
        return line.build();
    }

    /** Final action bar held for a few seconds after the scan settles. */
    public static Component settled(ScanSession session) {
        ScanJob job = session.job();
        ScanProgress.Snapshot snapshot = session.progress().snapshot();
        if (session.state() == ScanSession.State.FAILED) {
            return Component.text()
                    .append(Component.text("✖ ", Theme.BAD))
                    .append(Component.text(job.title() + " failed", Theme.BAD, TextDecoration.BOLD))
                    .append(SEPARATOR)
                    .append(Component.text(Format.failure(session.failure()), Theme.MUTED))
                    .build();
        }
        return Component.text()
                .append(Component.text("✔ ", Theme.GOOD))
                .append(Component.text(job.title() + " complete", Theme.GOOD, TextDecoration.BOLD))
                .append(SEPARATOR)
                .append(Component.text(Format.count(snapshot.done()) + " " + job.unit(), NamedTextColor.WHITE))
                .append(Component.text(" in " + Format.seconds(snapshot.elapsedNanos()), Theme.MUTED))
                .append(SEPARATOR)
                .append(counter(job.counter(), snapshot))
                .build();
    }

    /** Throttled console progress line. */
    public static Component consoleLine(ScanSession session, ScanProgress.Snapshot snapshot) {
        ScanJob job = session.job();
        TextComponent.Builder line = Component.text()
                .append(Component.text("[Scan #" + session.id() + "] ", Theme.DIM))
                .append(Component.text(job.title() + " ", Theme.PRIMARY));
        if (snapshot.determinate()) {
            line.append(Component.text(Format.percent(snapshot.ratio()), Theme.HIGHLIGHT))
                    .append(Component.text(" (" + Format.count(snapshot.done()) + "/" + Format.count(snapshot.total())
                            + " " + job.unit() + ")", Theme.MUTED));
        } else {
            line.append(Component.text(snapshot.phase() + "…", Theme.MUTED));
        }
        return line.append(Component.text(" · ", Theme.DIM))
                .append(counter(job.counter(), snapshot))
                .append(Component.text(" · " + Format.clock(snapshot.elapsedNanos()), Theme.MUTED))
                .build();
    }

    /** {@code ✦ 14 holders · 1,204 items}. */
    public static Component counter(ScanJob.Counter counter, ScanProgress.Snapshot snapshot) {
        TextComponent.Builder text = Component.text()
                .append(Component.text("✦ ", Theme.ACCENT))
                .append(Component.text(Format.count(snapshot.entries()), NamedTextColor.WHITE))
                .append(Component.text(" " + counter.entries(), Theme.MUTED));
        if (counter.amount() != null && snapshot.hasAmount()) {
            text.append(Component.text(" · ", Theme.DIM))
                    .append(Component.text(Format.count(snapshot.amount()), NamedTextColor.WHITE))
                    .append(Component.text(" " + counter.amount(), Theme.MUTED));
        }
        return text.build();
    }

    /** Gradient-filled progress bar. */
    public static Component bar(double ratio) {
        int filled = (int) Math.round(Math.max(0D, Math.min(1D, ratio)) * BAR_CELLS);
        TextComponent.Builder bar = Component.text();
        for (int cell = 0; cell < BAR_CELLS; cell++) {
            TextColor color = cell < filled
                    ? TextColor.lerp(cell / (float) (BAR_CELLS - 1), Theme.BAR_FROM, Theme.BAR_TO)
                    : Theme.DIM;
            bar.append(Component.text(CELL, color));
        }
        return bar.build();
    }

    /** Indeterminate bar: a short segment sweeping back and forth while the total is unknown. */
    private static Component sweep(int frame) {
        int span = BAR_CELLS - SWEEP_WIDTH;
        int position = frame % (span * 2);
        int start = position <= span ? position : span * 2 - position;
        TextComponent.Builder bar = Component.text();
        for (int cell = 0; cell < BAR_CELLS; cell++) {
            boolean lit = cell >= start && cell < start + SWEEP_WIDTH;
            bar.append(Component.text(CELL, lit ? Theme.BAR_TO : Theme.DIM));
        }
        return bar.build();
    }
}
