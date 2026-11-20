package me.stella.command.suggestion;

import java.util.Collection;
import org.bukkit.command.CommandSender;

/** Supplies candidates for one argument position; {@code args} are the sub-command arguments typed so far. */
@FunctionalInterface
public interface SuggestionProvider {
    Collection<String> suggest(CommandSender sender, String[] args);
}
