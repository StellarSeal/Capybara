package me.stella.command.capybara;

import me.stella.CapybaraScan;
import me.stella.command.annotations.SubCommandMeta;
import me.stella.scan.ScanJob;
import me.stella.ui.Messages;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@SubCommandMeta(root = "capybara", name = "ah", aliases = {"auction", "zah"}, permission = "capybara.scan",
        playerOnly = true, description = "Find the held item in zAuctionHouse listings, expired and bought items")
final class AuctionScanCommand extends HeldItemScanCommand {

    @Override
    protected void scan(Player player, ItemStack target, String subject, String seed, String[] args) {
        if (!CapybaraScan.supportZAuctionHouse()) {
            player.sendMessage(Messages.pluginMissing("zAuctionHouseV3"));
            return;
        }
        submit(player, new ScanJob("Auction scan", subject, "listings", ScanJob.Counter.OWNERS,
                ScanJob.Work.of(progress -> natives().auctionHouse(target, progress),
                        data -> Reports.scanByKey("auction", seed, data))));
    }
}
