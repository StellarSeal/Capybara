package me.stella.command.capybara;

import me.stella.Capybara;
import me.stella.CapybaraNative;
import me.stella.CapybaraScan;
import me.stella.command.SubCommandHandler;
import me.stella.scan.ScanJob;
import me.stella.scan.ScanService;
import me.stella.ui.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Base for scans that search for the item held in the administrator's main hand. */
abstract class HeldItemScanCommand implements SubCommandHandler {

    @Override
    public final void execute(CommandSender sender, String[] args) {
        Player player = (Player) sender;
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            player.sendMessage(Messages.emptyHand());
            return;
        }
        ItemStack target = hand.clone();
        String subject = CapybaraScan.toSearchItemID(target);
        scan(player, target, subject, subject + " $ " + player.getName(), args);
    }

    /**
     * @param subject search id of the held item
     * @param seed    report header ({@code <subject> $ <operator>})
     */
    protected abstract void scan(Player player, ItemStack target, String subject, String seed, String[] args);

    protected static CapybaraNative natives() {
        return CapybaraScan.get();
    }

    protected static void submit(Player player, ScanJob job) {
        ScanService scans = Capybara.scans();
        scans.submit(player, job);
    }
}
