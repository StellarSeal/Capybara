package me.stella.command.capybara.scan.player;

import me.stella.command.capybara.Reports;
import me.stella.command.capybara.scan.HeldItemScanCommand;
import me.stella.scan.model.ScanJob;

import me.stella.CapybaraScan;
import me.stella.command.annotations.SubCommandMeta;
import me.stella.ui.Messages;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@SubCommandMeta(root = "capybara", name = "vault", aliases = {"pv"}, permission = "capybara.scan",
        playerOnly = true, description = "Find the held item in every PlayerVaults vault")
final class VaultScanCommand extends HeldItemScanCommand {

    @Override
    protected void scan(Player player, ItemStack target, String subject, String seed, String[] args) {
        if (!CapybaraScan.supportPlayerVaults()) {
            player.sendMessage(Messages.pluginMissing("PlayerVaults"));
            return;
        }
        submit(player, new ScanJob("Vault scan", subject, "vault files", ScanJob.Counter.HOLDERS,
                ScanJob.Work.of(progress -> natives().vault(target, progress),
                        pools -> Reports.scan("vault", seed, Reports.flatten(pools)))));
    }
}
