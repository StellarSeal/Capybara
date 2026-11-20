package me.stella.command.capybara.scan;

import me.stella.utility.Reports;
import me.stella.command.suggestion.*;
import me.stella.scan.model.ScanJob;

import java.util.Map;
import me.stella.Capybara;
import me.stella.CapybaraScan;
import me.stella.command.annotations.SubCommandMeta;
import me.stella.ui.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@SubCommandMeta(root = "capybara", name = "home", usage = "<radius>", minArgs = 1, permission = "capybara.scan",
        playerOnly = true, description = "Find the Essentials home / last location closest to you within a radius")
final class HomeScanCommand implements SubCommandHandler {

    private static final Map<Integer, SuggestionProvider> SUGGESTIONS = Map.of(0, Suggestions.of("16", "32", "64", "128"));

    @Override
    public Map<Integer, SuggestionProvider> suggestions() {
        return SUGGESTIONS;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = (Player) sender;
        double radius;
        try {
            radius = Double.parseDouble(args[0]);
        } catch (NumberFormatException e) {
            player.sendMessage(Messages.invalidNumber(args[0]));
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("Essentials") == null) {
            player.sendMessage(Messages.pluginMissing("Essentials"));
            return;
        }
        Location point = player.getLocation();
        String seed = CapybaraScan.toSearchItemID(player.getInventory().getItemInMainHand()) + " $ " + player.getName();
        Capybara.scans().submit(player, new ScanJob("Home scan", "r = " + args[0], "users", ScanJob.Counter.MATCHES,
                ScanJob.Work.of(progress -> CapybaraScan.get().scan(point, radius, progress),
                        data -> Reports.distances("home", seed, data))));
    }
}
