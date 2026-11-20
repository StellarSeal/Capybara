package me.stella.command.capybara;

import me.stella.command.annotations.SubCommandMeta;
import me.stella.scan.ScanJob;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@SubCommandMeta(root = "capybara", name = "ec", aliases = {"ender", "enderchest"}, permission = "capybara.scan",
        playerOnly = true, description = "Find the held item in every ender chest")
final class EnderScanCommand extends HeldItemScanCommand {

    @Override
    protected void scan(Player player, ItemStack target, String subject, String seed, String[] args) {
        Bukkit.getOnlinePlayers().forEach(Player::saveData);
        submit(player, new ScanJob("Ender chest scan", subject, "players", ScanJob.Counter.HOLDERS,
                ScanJob.Work.of(progress -> natives().ender(target, progress),
                        data -> Reports.scan("ender", seed, data))));
    }
}
