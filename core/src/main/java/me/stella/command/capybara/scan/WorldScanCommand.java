package me.stella.command.capybara.scan;

import java.time.Instant;
import java.util.Map;
import me.stella.command.annotations.SubCommandMeta;
import me.stella.command.suggestion.SuggestionProvider;
import me.stella.command.suggestion.Suggestions;
import me.stella.scan.model.ScanJob;
import me.stella.ui.Format;
import me.stella.ui.Messages;
import me.stella.utility.Reports;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@SubCommandMeta(root = "capybara", name = "world", usage = "<world> [since]", minArgs = 1, permission = "capybara.scan",
      playerOnly = true, description = "Find the held item in containers and entities of a world")
final class WorldScanCommand extends HeldItemScanCommand {

   private static final Map<Integer, SuggestionProvider> SUGGESTIONS = Map.of(
         0, Suggestions.worlds(),
         1, Suggestions.of("1h", "24h", "7d", "30d"));

   @Override
   public Map<Integer, SuggestionProvider> suggestions() {
      return SUGGESTIONS;
   }

   @Override
   protected void scan(Player player, ItemStack target, String subject, String seed, String[] args) {
      World world = Bukkit.getWorld(args[0]);
      if (world == null) {
         player.sendMessage(Messages.unknownWorld(args[0]));
         return;
      }

      long minTimestampSec = 0L;
      String suffix = "";
      if (args.length >= 2) {
         long seconds = Format.parseDurationSeconds(args[1]);
         if (seconds < 0L) {
            player.sendMessage(Messages.invalidDuration(args[1]));
            return;
         }
         minTimestampSec = Math.max(1L, Instant.now().getEpochSecond() - seconds);
         suffix = " (" + args[1] + ")";
      }

      String name = world.getName();
      long cutoff = minTimestampSec;
      submit(player, new ScanJob("World scan · " + name + suffix, subject, "regions", ScanJob.Counter.CONTAINERS,
            ScanJob.Work.of(progress -> natives().world(target, name, cutoff, progress),
                  data -> Reports.scan("world_" + name, seed, data))));
   }
}
