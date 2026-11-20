package me.stella.command;

import java.util.List;
import java.util.Map;
import me.stella.command.annotations.SubCommandMeta;
import org.bukkit.command.CommandSender;

/**
 * A sub-command. Implementations are annotated with {@link SubCommandMeta}, need a no-arg constructor and are
 * discovered automatically. The arguments passed in never include the sub-command name itself.
 */
public interface SubCommandHandler {

    void execute(CommandSender sender, String[] args);

    /**
     * Positional suggestions: key {@code 0} completes the first argument after the sub-command name, and so on.
     * Override {@link #tabComplete(CommandSender, String[])} instead for fully custom completion.
     */
    default Map<Integer, SuggestionProvider> suggestions() {
        return Map.of();
    }

    default List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 0) {
            return List.of();
        }
        SuggestionProvider provider = suggestions().get(args.length - 1);
        return provider == null ? List.of() : Suggestions.filter(provider.suggest(sender, args), args[args.length - 1]);
    }

    default SubCommandMeta metadata() {
        SubCommandMeta meta = getClass().getAnnotation(SubCommandMeta.class);
        if (meta == null) {
            throw new IllegalStateException("Sub-command is missing @SubCommandMeta: " + getClass().getName());
        }
        return meta;
    }
}
