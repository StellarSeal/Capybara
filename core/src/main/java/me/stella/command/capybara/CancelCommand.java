package me.stella.command.capybara;

import java.util.Optional;
import me.stella.Capybara;
import me.stella.command.annotations.SubCommandMeta;
import me.stella.command.suggestion.SubCommandHandler;
import me.stella.scan.model.ScanSession;
import me.stella.ui.Messages;
import org.bukkit.command.CommandSender;

@SubCommandMeta(root = "capybara", name = "cancel", aliases = {"stop", "abort"}, permission = "capybara.admin",
      description = "Cancel your currently running scan")
final class CancelCommand implements SubCommandHandler {

   @Override
   public void execute(CommandSender sender, String[] args) {
      Optional<ScanSession> cancelled = Capybara.scans().cancel(sender);
      if (cancelled.isEmpty()) {
         sender.sendMessage(Messages.noScanToCancel());
         return;
      }
      sender.sendMessage(Messages.scanCancelling(cancelled.get()));
   }
}
