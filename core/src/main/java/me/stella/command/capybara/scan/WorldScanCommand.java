package me.stella.command.capybara.scan;

import me.stella.command.capybara.Reports;
import me.stella.command.suggestion.*;
import me.stella.scan.model.ScanJob;

import java.util.Map;
import me.stella.command.annotations.SubCommandMeta;
import me.stella.ui.Messages;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@SubCommandMeta(root = "capybara", name = "world", usage = "<world>", minArgs = 1, permission = "capybara.scan",
        playerOnly = true, description = "Find the held item in every container and entity of a world")
final class WorldScanCommand extends HeldItemScanCommand {

    private static final Map<Integer, SuggestionProvider> SUGGESTIONS = Map.of(0, Suggestions.worlds());

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
        String name = world.getName();
        submit(player, new ScanJob("World scan Â· " + name, subject, "regions", ScanJob.Counter.CONTAINERS,
                ScanJob.Work.of(progress -> natives().world(target, name, progress),
                        data -> Reports.scan("world_" + name, seed, data))));
    }
}
