package me.stella.command.capybara;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import me.stella.Capybara;
import me.stella.CapybaraNative;
import me.stella.CapybaraScan;
import me.stella.command.annotations.SubCommandMeta;
import me.stella.command.suggestion.SubCommandHandler;
import me.stella.command.suggestion.SuggestionProvider;
import me.stella.command.suggestion.Suggestions;
import me.stella.scan.model.ScanJob;
import me.stella.ui.Format;
import me.stella.ui.Messages;
import me.stella.utility.Reports;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Tallies every item of a storage region; no held item needed and usable from console. */
@SubCommandMeta(root = "capybara", name = "poll", usage = "<inv|ec|vault|ah|world> [world] [since]", minArgs = 1,
      permission = "capybara.poll", description = "Tally every item stored in a region")
final class PollCommand implements SubCommandHandler {

   private static final Map<Integer, SuggestionProvider> SUGGESTIONS = Map.of(
         0, Suggestions.of("inv", "ec", "vault", "ah", "world"),
         1, Suggestions.when(0, "world", Suggestions.worlds()),
         2, Suggestions.when(0, "world", Suggestions.of("1h", "24h", "7d", "30d")));

   @Override
   public Map<Integer, SuggestionProvider> suggestions() {
      return SUGGESTIONS;
   }

   @Override
   public void execute(CommandSender sender, String[] args) {
      CapybaraNative natives = CapybaraScan.get();
      ScanJob job = switch (args[0].toLowerCase(Locale.ROOT)) {
         case "inv" -> {
            Bukkit.getOnlinePlayers().forEach(Player::saveData);
            yield job("Inventory poll", "players", ScanJob.Work.of(natives::pollInventory,
                  data -> Reports.poll("Inventory Poll", "inv", data)));
         }
         case "ec" -> {
            Bukkit.getOnlinePlayers().forEach(Player::saveData);
            yield job("Ender chest poll", "players", ScanJob.Work.of(natives::pollEnderChest,
                  data -> Reports.poll("Ender Chest Poll", "ec", data)));
         }
         case "vault" -> {
            if (!CapybaraScan.supportPlayerVaults()) {
               sender.sendMessage(Messages.pluginMissing("PlayerVaults"));
               yield null;
            }
            yield job("Vault poll", "vault files", ScanJob.Work.of(natives::pollVault,
                  data -> Reports.poll("Vault Poll", "vault", data)));
         }
         case "ah", "auction" -> {
            if (!CapybaraScan.supportZAuctionHouse()) {
               sender.sendMessage(Messages.pluginMissing("zAuctionHouseV3"));
               yield null;
            }
            yield job("Auction poll", "listings", ScanJob.Work.of(natives::pollAuctionHouse,
                  data -> Reports.poll("Auction House Poll", "ah", data)));
         }
         case "world" -> worldJob(sender, natives, args);
         default -> {
            sender.sendMessage(Messages.unknownRegion(args[0]));
            yield null;
         }
      };
      if (job != null) {
         Capybara.scans().submit(sender, job);
      }
   }

   private static ScanJob worldJob(CommandSender sender, CapybaraNative natives, String[] args) {
      if (args.length < 2) {
         sender.sendMessage(Messages.usage("capybara", PollCommand.class.getAnnotation(SubCommandMeta.class)));
         return null;
      }
      World world = Bukkit.getWorld(args[1]);
      if (world == null) {
         sender.sendMessage(Messages.unknownWorld(args[1]));
         return null;
      }

      long minTimestampSec = 0L;
      String suffix = "";
      if (args.length >= 3) {
         long seconds = Format.parseDurationSeconds(args[2]);
         if (seconds < 0L) {
            sender.sendMessage(Messages.invalidDuration(args[2]));
            return null;
         }
         minTimestampSec = Math.max(1L, Instant.now().getEpochSecond() - seconds);
         suffix = " (" + args[2] + ")";
      }

      String name = world.getName();
      long cutoff = minTimestampSec;
      return new ScanJob("World poll · " + name + suffix, "all items", "regions", ScanJob.Counter.ITEM_TYPES,
            ScanJob.Work.of(progress -> natives.pollWorld(name, cutoff, progress),
                  data -> Reports.poll("World Poll (" + name + ")", "world_" + name, data)));
   }

   private static ScanJob job(String title, String unit, ScanJob.Work work) {
      return new ScanJob(title, "all items", unit, ScanJob.Counter.ITEM_TYPES, work);
   }
}
