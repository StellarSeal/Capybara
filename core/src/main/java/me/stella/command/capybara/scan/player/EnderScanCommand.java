package me.stella.command.capybara.scan.player;

import me.stella.utility.Reports;
import me.stella.command.capybara.scan.HeldItemScanCommand;
import me.stella.scan.model.ScanJob;

import me.stella.command.annotations.SubCommandMeta;
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
