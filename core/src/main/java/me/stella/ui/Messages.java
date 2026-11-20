package me.stella.ui;

import java.io.File;
import me.stella.Capybara;
import me.stella.command.annotations.SubCommandMeta;
import me.stella.scan.model.ScanJob;
import me.stella.scan.model.ScanProgress;
import me.stella.scan.monitor.ScanRenderer;
import me.stella.scan.model.ScanSession;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/** Every chat message Capybara sends, as MiniMessage templates over the {@link Theme} palette. */
public final class Messages {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final String PREFIX = "<gradient:#e3b98a:#f6dcb4><bold>Capybara</bold></gradient> <dim>»</dim> ";

    private Messages() {
    }

    /** Prefixed MiniMessage line; user-provided values must be passed as {@link Placeholder#unparsed} resolvers. */
    public static Component line(String template, TagResolver... resolvers) {
        return raw(PREFIX + template, resolvers);
    }

    public static Component raw(String template, TagResolver... resolvers) {
        return MINI.deserialize(template, TagResolver.resolver(Theme.TAGS, TagResolver.resolver(resolvers)));
    }

    // ---------------------------------------------------------------- command framework

    public static Component noPermission() {
        return line("<bad>You do not have permission to do that.");
    }

    public static Component playerOnly() {
        return line("<bad>This command can only be used in-game.");
    }

    public static Component unknownSubCommand(String sub) {
        return line("<bad>Unknown sub-command <white><sub></white>. <muted>Showing help:",
                Placeholder.unparsed("sub", sub));
    }

    public static Component usage(String label, SubCommandMeta meta) {
        return line("<muted>Usage: <fill><hover:show_text:'<muted>Click to fill in'>"
                        + "<white>/<label> <name></white> <highlight><usage></highlight></hover></fill>",
                fill(label, meta), Placeholder.unparsed("label", label), Placeholder.unparsed("name", meta.name()),
                Placeholder.unparsed("usage", meta.usage()));
    }

    public static Component internalError(Throwable failure) {
        return line("<bad>Something went wrong: <white><error></white><muted> - see console.",
                Placeholder.unparsed("error", Format.failure(failure)));
    }

    public static Component helpHeader(String version) {
        return raw("<dim><strikethrough>          </strikethrough></dim> "
                        + "<gradient:#e3b98a:#f6dcb4><bold>Capybara</bold></gradient> <dim>v<version></dim> "
                        + "<dim><strikethrough>          </strikethrough></dim>",
                Placeholder.unparsed("version", version));
    }

    public static Component helpEntry(String label, SubCommandMeta meta) {
        String aliases = meta.aliases().length == 0 ? "none" : String.join(", ", meta.aliases());
        return raw("<dim> ▪</dim> <fill><hover:show_text:'"
                        + "<primary>/<label> <name></primary> <highlight><usage></highlight><newline>"
                        + "<muted><description></muted><newline><newline>"
                        + "<dim>Aliases:</dim> <white><aliases></white><newline>"
                        + "<dim>Permission:</dim> <white><permission></white>"
                        + (meta.playerOnly() ? "<newline><dim>In-game only</dim>" : "")
                        + "<newline><newline><accent>Click to fill in'><primary>/<label> <name></primary>"
                        + " <highlight><usage></highlight></hover></fill> <dim>-</dim> <muted><description>",
                fill(label, meta), Placeholder.unparsed("label", label), Placeholder.unparsed("name", meta.name()),
                Placeholder.unparsed("usage", meta.usage()), Placeholder.unparsed("description", meta.description()),
                Placeholder.unparsed("aliases", aliases),
                Placeholder.unparsed("permission", meta.permission().isEmpty() ? "none" : meta.permission()));
    }

    public static Component helpFooter() {
        return raw("<dim>   Hover an entry for details, click to fill it in.");
    }

    // ---------------------------------------------------------------- scan arguments

    public static Component emptyHand() {
        return line("<bad>Hold the item you want to scan for in your main hand.");
    }

    public static Component pluginMissing(String plugin) {
        return line("<bad><plugin> is not installed or not enabled - nothing to scan.",
                Placeholder.unparsed("plugin", plugin));
    }

    public static Component unknownWorld(String world) {
        return line("<bad>Unknown world <white><world></white>.", Placeholder.unparsed("world", world));
    }

    public static Component invalidNumber(String input) {
        return line("<bad><white><input></white> is not a valid number.", Placeholder.unparsed("input", input));
    }

    public static Component invalidDuration(String input) {
        return line("<bad><white><input></white> is not a valid duration. <muted>Examples: <white>30m, 24h, 7d",
                Placeholder.unparsed("input", input));
    }

    public static Component noScanToCancel() {
        return line("<muted>You have no running scan to cancel.");
    }

    public static Component scanCancelling(ScanSession session) {
        return line("<highlight>Cancelling <primary><title></primary> <dim>#<id></dim><highlight>…",
                Placeholder.unparsed("title", session.job().title()),
                Placeholder.unparsed("id", String.valueOf(session.id())));
    }

    public static Component unknownRegion(String region) {
        return line("<bad>Unknown region <white><region></white>. <muted>Valid: <white>inv, ec, vault, ah, world",
                Placeholder.unparsed("region", region));
    }

    // ---------------------------------------------------------------- scan lifecycle

    public static Component scanStarted(ScanSession session) {
        ScanJob job = session.job();
        return line("<muted>Started <primary><title></primary> <dim>#<id></dim> <muted>for</muted> "
                        + "<hover:show_text:'<muted>Subject: <white><subject></white><newline><muted>Progress is shown"
                        + " on your action bar.'><white><subject></white></hover>",
                Placeholder.unparsed("title", job.title()), Placeholder.unparsed("id", String.valueOf(session.id())),
                Placeholder.unparsed("subject", job.subject()));
    }

    public static Component alreadyScanning(ScanSession running) {
        ScanProgress.Snapshot snapshot = running.progress().snapshot();
        return line("<bad>You already have a scan running: <primary><title></primary> <dim>#<id></dim> "
                        + "<highlight><progress></highlight><bad>. Wait for it to finish.",
                Placeholder.unparsed("title", running.job().title()),
                Placeholder.unparsed("id", String.valueOf(running.id())),
                Placeholder.unparsed("progress", snapshot.determinate() ? Format.percent(snapshot.ratio()) : snapshot.phase()));
    }

    public static Component scanSettled(ScanSession session) {
        ScanJob job = session.job();
        ScanProgress.Snapshot snapshot = session.progress().snapshot();
        if (session.state() == ScanSession.State.CANCELLED) {
            return line("<highlight>■ <title> cancelled</highlight> <muted>after <white><time></white> "
                            + "(<done>/<total> <unit>)",
                    Placeholder.unparsed("title", job.title()),
                    Placeholder.unparsed("time", Format.seconds(snapshot.elapsedNanos())),
                    Placeholder.unparsed("done", Format.count(snapshot.done())),
                    Placeholder.unparsed("total", Format.count(snapshot.total())),
                    Placeholder.unparsed("unit", job.unit()));
        }
        if (session.state() == ScanSession.State.FAILED) {
            return line("<bad>✖ <title> failed after <time>: <white><error></white><newline>"
                            + "<dim>   ↳ </dim><muted>The full stack trace is in the console.",
                    Placeholder.unparsed("title", job.title()),
                    Placeholder.unparsed("time", Format.seconds(snapshot.elapsedNanos())),
                    Placeholder.unparsed("error", Format.failure(session.failure())));
        }
        File report = session.report();
        String shown = report == null ? "-" : relative(report);
        String path = report == null ? "" : report.getAbsolutePath();
        String errorSuffix = snapshot.errors() > 0L
                ? " <dim>·</dim> <highlight>" + Format.count(snapshot.errors()) + " skipped</highlight>"
                : "";
        return line("<good>✔ <title> complete</good> <muted>in <white><time></white> - </muted><counter>"
                        + errorSuffix + "<newline>"
                        + "<dim>   ↳ </dim><muted>Report: <copy><hover:show_text:'<muted><path><newline>"
                        + "<accent>Click to copy the path'><white><underlined><report></underlined></white></hover></copy>",
                Placeholder.unparsed("title", job.title()),
                Placeholder.unparsed("time", Format.seconds(snapshot.elapsedNanos())),
                Placeholder.component("counter", ScanRenderer.counter(job.counter(), snapshot)),
                Placeholder.styling("copy", ClickEvent.copyToClipboard(path)),
                Placeholder.unparsed("report", shown), Placeholder.unparsed("path", path));
    }

    public static Component noScansRunning() {
        return line("<muted>No scans are running.");
    }

    public static Component statusHeader(int count) {
        return line("<muted><count> scan(s) running:", Placeholder.unparsed("count", String.valueOf(count)));
    }

    public static Component statusEntry(ScanSession session) {
        ScanProgress.Snapshot snapshot = session.progress().snapshot();
        String progress = snapshot.determinate()
                ? Format.percent(snapshot.ratio()) + " (" + Format.count(snapshot.done()) + "/"
                        + Format.count(snapshot.total()) + " " + session.job().unit() + ")"
                : snapshot.phase() + "…";
        return raw("<dim>   #<id></dim> <primary><title></primary> <dim>·</dim> <white><owner></white> <dim>·</dim> "
                        + "<highlight><progress></highlight> <dim>·</dim> <counter> <dim>·</dim> <muted><time>",
                Placeholder.unparsed("id", String.valueOf(session.id())),
                Placeholder.unparsed("title", session.job().title()), Placeholder.unparsed("owner", session.ownerName()),
                Placeholder.unparsed("progress", progress),
                Placeholder.component("counter", ScanRenderer.counter(session.job().counter(), snapshot)),
                Placeholder.unparsed("time", Format.clock(snapshot.elapsedNanos())));
    }

    // ---------------------------------------------------------------- nuke

    public static Component nuked(int containers, String scope) {
        return line("<good>Cleared <white><count></white> container(s) <scope>.",
                Placeholder.unparsed("count", String.valueOf(containers)), Placeholder.unparsed("scope", scope));
    }

    public static Component noContainerTargeted() {
        return line("<bad>Look at a container within 4 blocks.");
    }

    private static TagResolver fill(String label, SubCommandMeta meta) {
        return Placeholder.styling("fill", ClickEvent.suggestCommand("/" + label + " " + meta.name() + " "));
    }

    private static String relative(File report) {
        String base = Capybara.get().getDataFolder().getAbsolutePath();
        String path = report.getAbsolutePath();
        return (path.startsWith(base) ? path.substring(base.length() + 1) : path).replace('\\', '/');
    }
}
