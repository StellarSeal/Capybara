package me.stella.command.suggestion;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** Stock {@link SuggestionProvider}s. */
public final class Suggestions {

    private Suggestions() {
    }

    public static SuggestionProvider of(String... values) {
        List<String> fixed = List.of(values);
        return (sender, args) -> fixed;
    }

    public static SuggestionProvider worlds() {
        return (sender, args) -> Bukkit.getWorlds().stream().map(World::getName).toList();
    }

    public static SuggestionProvider players() {
        return (sender, args) -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
    }

    /** Only offers {@code then} when the argument at {@code index} equals {@code value}. */
    public static SuggestionProvider when(int index, String value, SuggestionProvider then) {
        return (sender, args) -> args.length > index && args[index].equalsIgnoreCase(value)
                ? then.suggest(sender, args)
                : List.of();
    }

    /** Case-insensitive prefix filter. */
    public static List<String> filter(Collection<String> candidates, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return candidates.stream().filter(c -> c.toLowerCase(Locale.ROOT).startsWith(lower)).sorted().toList();
    }
}
