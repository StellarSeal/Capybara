package me.stella.command.capybara;

import java.util.Collection;
import me.stella.Capybara;
import me.stella.command.SubCommandHandler;
import me.stella.command.annotations.SubCommandMeta;
import me.stella.scan.ScanSession;
import me.stella.ui.Messages;
import org.bukkit.command.CommandSender;

@SubCommandMeta(root = "capybara", name = "status", aliases = {"scans"}, permission = "capybara.admin",
        description = "List running scans and their progress")
final class StatusCommand implements SubCommandHandler {

    @Override
    public void execute(CommandSender sender, String[] args) {
        Collection<ScanSession> sessions = Capybara.scans().sessions();
        if (sessions.isEmpty()) {
            sender.sendMessage(Messages.noScansRunning());
            return;
        }
        sender.sendMessage(Messages.statusHeader(sessions.size()));
        sessions.forEach(session -> sender.sendMessage(Messages.statusEntry(session)));
    }
}
