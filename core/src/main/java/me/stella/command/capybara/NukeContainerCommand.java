package me.stella.command.capybara;

import me.stella.command.SubCommandHandler;
import me.stella.command.annotations.SubCommandMeta;
import me.stella.ui.Messages;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@SubCommandMeta(root = "capybara", name = "nuke-container", permission = "capybara.nuke", playerOnly = true,
        description = "Empty the container you are looking at")
final class NukeContainerCommand implements SubCommandHandler {

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = (Player) sender;
        Block target = player.getTargetBlock(null, 4);
        if (!(target.getState() instanceof Container container)) {
            player.sendMessage(Messages.noContainerTargeted());
            return;
        }
        container.getInventory().clear();
        player.sendMessage(Messages.nuked(1, "at " + target.getX() + ", " + target.getY() + ", " + target.getZ()));
    }
}
