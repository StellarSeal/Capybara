package me.stella.command.capybara;

import me.stella.command.annotations.SubCommandMeta;
import me.stella.scan.ScanJob;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@SubCommandMeta(root = "capybara", name = "inv", aliases = {"inventory"}, permission = "capybara.scan",
        playerOnly = true, description = "Find the held item in every player inventory")
final class InventoryScanCommand extends HeldItemScanCommand {

    @Override
    protected void scan(Player player, ItemStack target, String subject, String seed, String[] args) {
        Bukkit.getOnlinePlayers().forEach(Player::saveData);
        submit(player, new ScanJob("Inventory scan", subject, "players", ScanJob.Counter.HOLDERS,
                ScanJob.Work.of(progress -> natives().inventory(target, progress),
                        data -> Reports.scan("inv", seed, data))));
    }
}
