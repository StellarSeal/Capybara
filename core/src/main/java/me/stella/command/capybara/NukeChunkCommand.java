package me.stella.command.capybara;

import me.stella.command.SubCommandHandler;
import me.stella.command.annotations.SubCommandMeta;
import me.stella.ui.Messages;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@SubCommandMeta(root = "capybara", name = "nuke-chunk", permission = "capybara.nuke", playerOnly = true,
        description = "Empty every container in the chunk you stand in")
final class NukeChunkCommand implements SubCommandHandler {

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = (Player) sender;
        int cleared = 0;
        for (BlockState state : player.getLocation().getChunk().getTileEntities()) {
            if (state instanceof Container container) {
                container.getInventory().clear();
                cleared++;
            }
        }
        player.sendMessage(Messages.nuked(cleared, "in this chunk"));
    }
}
