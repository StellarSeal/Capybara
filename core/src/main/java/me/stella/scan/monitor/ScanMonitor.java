package me.stella.scan.monitor;

import me.stella.scan.model.*;
import me.stella.scan.engine.ScanService;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.concurrent.TimeUnit;
import me.stella.ui.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Observer agent bound to one {@link ScanSession} and one audience.
 *
 * <p>It samples the session's {@link ScanProgress} on a fixed cadence and renders it: a live action bar for players,
 * throttled log lines for the console. Once the scan settles it announces the outcome in chat, holds the final action
 * bar for a moment and retires itself. Players are observed on their own entity scheduler and the console on the async
 * scheduler, so the monitor is safe on both Paper and Folia.</p>
 */
public final class ScanMonitor {

    private static final long PLAYER_PERIOD_TICKS = 4L;
    private static final long CONSOLE_PERIOD_MILLIS = 250L;
    private static final long CONSOLE_LOG_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(5L);
    private static final long LINGER_NANOS = TimeUnit.SECONDS.toNanos(4L);

    private final ScanService service;
    private final ScanSession session;
    private final CommandSender audience;
    private final boolean actionBar;
    private volatile ScheduledTask task;
    private int frame;
    private long lastLogNanos = System.nanoTime();
    private long settledAtNanos = -1L;

    private ScanMonitor(ScanService service, ScanSession session, CommandSender audience, boolean actionBar) {
        this.service = service;
        this.session = session;
        this.audience = audience;
        this.actionBar = actionBar;
    }

    public static ScanMonitor start(Plugin plugin, ScanService service, CommandSender owner, ScanSession session) {
        if (owner instanceof Player player) {
            ScanMonitor monitor = new ScanMonitor(service, session, player, true);
            monitor.task = player.getScheduler().runAtFixedRate(plugin, monitor::tick,
                    () -> service.handOver(monitor, session), 1L, PLAYER_PERIOD_TICKS);
            if (monitor.task != null) {
                return monitor;
            }
            // the player is already gone - fall back to the console
        }
        ScanMonitor monitor = new ScanMonitor(service, session, Bukkit.getConsoleSender(), false);
        monitor.task = Bukkit.getAsyncScheduler().runAtFixedRate(plugin, monitor::tick,
                0L, CONSOLE_PERIOD_MILLIS, TimeUnit.MILLISECONDS);
        return monitor;
    }

    private void tick(ScheduledTask self) {
        frame++;
        long now = System.nanoTime();
        if (session.running()) {
            ScanProgress.Snapshot snapshot = session.progress().snapshot();
            if (actionBar) {
                audience.sendActionBar(ScanRenderer.progress(session, snapshot, frame));
            } else if (now - lastLogNanos >= CONSOLE_LOG_INTERVAL_NANOS) {
                lastLogNanos = now;
                audience.sendMessage(ScanRenderer.consoleLine(session, snapshot));
            }
            return;
        }
        if (settledAtNanos < 0L) {
            settledAtNanos = now;
            audience.sendMessage(Messages.scanSettled(session));
        }
        if (actionBar && now - settledAtNanos < LINGER_NANOS) {
            audience.sendActionBar(ScanRenderer.settled(session));
            return;
        }
        self.cancel();
        service.release(this);
    }

    public void cancel() {
        ScheduledTask current = task;
        if (current != null) {
            current.cancel();
        }
    }
}
