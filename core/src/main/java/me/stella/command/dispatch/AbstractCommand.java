package me.stella.command.dispatch;

import me.stella.command.suggestion.SubCommandHandler;

import java.util.List;
import me.stella.command.annotations.CommandMeta;
import me.stella.ui.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** Root command configured from its {@link CommandMeta}; guards player-only and permission for execution and completion. */
public abstract class AbstractCommand extends Command implements TabCompleter {

    protected AbstractCommand() {
        super("");
        CommandMeta meta = metadata();
        setName(meta.name());
        setLabel(meta.name());
        if (!meta.description().isEmpty()) {
            setDescription(meta.description());
        }
        if (!meta.usage().isEmpty()) {
            setUsage(meta.usage());
        }
        if (!meta.permission().isEmpty()) {
            setPermission(meta.permission());
        }
        if (meta.aliases().length > 0) {
            setAliases(List.of(meta.aliases()));
        }
    }

    /** Runs the command once the player-only and permission guards passed. */
    public abstract void dispatch(CommandSender sender, String label, String[] args);

    public abstract List<String> complete(CommandSender sender, String[] args);

    @Override
    public final boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        CommandMeta meta = metadata();
        if (meta.playerOnly() && !(sender instanceof Player)) {
            sender.sendMessage(Messages.playerOnly());
            return true;
        }
        if (!meta.permission().isEmpty() && !sender.hasPermission(meta.permission())) {
            sender.sendMessage(Messages.noPermission());
            return true;
        }
        dispatch(sender, commandLabel, args);
        return true;
    }

    @Override
    public final List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                            @NotNull String alias, @NotNull String[] args) {
        CommandMeta meta = metadata();
        if (!meta.permission().isEmpty() && !sender.hasPermission(meta.permission())) {
            return List.of();
        }
        return complete(sender, args);
    }

    @Override
    public final @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias,
                                                   @NotNull String[] args) throws IllegalArgumentException {
        // route native CommandMap completion through the same guarded path
        return onTabComplete(sender, this, alias, args);
    }

    public final CommandMeta metadata() {
        CommandMeta meta = getClass().getAnnotation(CommandMeta.class);
        if (meta == null) {
            throw new IllegalStateException("Command is missing @CommandMeta: " + getClass().getName());
        }
        return meta;
    }
}
