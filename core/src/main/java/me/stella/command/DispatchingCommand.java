package me.stella.command;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import me.stella.Capybara;
import me.stella.command.annotations.SubCommandMeta;
import me.stella.ui.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Root command that dispatches to the {@link SubCommandHandler}s registered for its name.
 *
 * <p>Builds a flow chart of sub-commands (names + aliases) and a root suggestion map at construction, enforces each
 * sub-command's player-only / permission / minimum-argument contract, strips the sub-command name from the arguments
 * handed to it and renders an interactive help board from the sub-command metadata.</p>
 */
public abstract class DispatchingCommand extends AbstractCommand {

    private static final String HELP = "help";

    private final Map<String, SubCommandHandler> subCommands = new LinkedHashMap<>();
    private final Map<String, SubCommandHandler> flowChart = new LinkedHashMap<>();
    private final List<String> rootSuggestions = new ArrayList<>();

    protected DispatchingCommand() {
        discoverSubCommands();
        buildSuggestionMap();
    }

    private void discoverSubCommands() {
        List<SubCommandHandler> handlers = new ArrayList<>();
        for (Class<? extends SubCommandHandler> type : SubCommandRegistry.get(metadata().name())) {
            try {
                Constructor<? extends SubCommandHandler> constructor = type.getDeclaredConstructor();
                constructor.setAccessible(true);
                handlers.add(constructor.newInstance());
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Could not load sub-command: " + type.getName(), e);
            }
        }
        handlers.sort((a, b) -> a.metadata().name().compareToIgnoreCase(b.metadata().name()));
        for (SubCommandHandler handler : handlers) {
            SubCommandMeta meta = handler.metadata();
            subCommands.put(key(meta.name()), handler);
            flowChart.put(key(meta.name()), handler);
            for (String alias : meta.aliases()) {
                flowChart.putIfAbsent(key(alias), handler);
            }
        }
    }

    private void buildSuggestionMap() {
        rootSuggestions.add(HELP);
        rootSuggestions.addAll(subCommands.keySet());
        rootSuggestions.sort(String::compareToIgnoreCase);
    }

    @Override
    public void dispatch(CommandSender sender, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase(HELP)) {
            sendHelp(sender, label);
            return;
        }
        SubCommandHandler handler = flowChart.get(key(args[0]));
        if (handler == null) {
            sender.sendMessage(Messages.unknownSubCommand(args[0]));
            sendHelp(sender, label);
            return;
        }
        SubCommandMeta meta = handler.metadata();
        if (meta.playerOnly() && !(sender instanceof Player)) {
            sender.sendMessage(Messages.playerOnly());
            return;
        }
        if (!permitted(sender, meta)) {
            sender.sendMessage(Messages.noPermission());
            return;
        }
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        if (subArgs.length < meta.minArgs()) {
            sender.sendMessage(Messages.usage(label, meta));
            return;
        }
        try {
            handler.execute(sender, subArgs);
        } catch (RuntimeException failure) {
            Capybara.get().getLogger().log(Level.SEVERE,
                    "Sub-command /" + label + " " + meta.name() + " failed", failure);
            sender.sendMessage(Messages.internalError(failure));
        }
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : key(args[0]);
            List<String> output = new ArrayList<>();
            for (String name : rootSuggestions) {
                SubCommandHandler handler = subCommands.get(name);
                if (handler != null && !visible(sender, handler.metadata())) {
                    continue;
                }
                if (name.startsWith(prefix)) {
                    output.add(name);
                }
            }
            return output;
        }
        SubCommandHandler handler = flowChart.get(key(args[0]));
        if (handler == null || !visible(sender, handler.metadata())) {
            return List.of();
        }
        List<String> suggestions = handler.tabComplete(sender, Arrays.copyOfRange(args, 1, args.length));
        return suggestions == null ? List.of() : suggestions;
    }

    /** Interactive help board listing the sub-commands visible to {@code sender}. */
    protected void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(Messages.helpHeader(Capybara.get().getPluginMeta().getVersion()));
        for (SubCommandHandler handler : subCommands.values()) {
            SubCommandMeta meta = handler.metadata();
            if (visible(sender, meta)) {
                sender.sendMessage(Messages.helpEntry(label, meta));
            }
        }
        sender.sendMessage(Messages.helpFooter());
    }

    private static boolean visible(CommandSender sender, SubCommandMeta meta) {
        return (!meta.playerOnly() || sender instanceof Player) && permitted(sender, meta);
    }

    private static boolean permitted(CommandSender sender, SubCommandMeta meta) {
        return meta.permission().isEmpty() || sender.hasPermission(meta.permission());
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
