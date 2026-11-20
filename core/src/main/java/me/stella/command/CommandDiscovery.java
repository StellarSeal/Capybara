package me.stella.command;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.stream.Stream;
import me.stella.command.annotations.CommandMeta;
import me.stella.command.annotations.SubCommandMeta;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Discovers {@link CommandMeta} root commands and {@link SubCommandMeta} sub-commands under a package of the plugin
 * jar. Sub-commands are registered first so every root finds its handlers when it is constructed. Roots declared in
 * plugin.yml are bound to their {@link PluginCommand}; others are registered on the command map for this session.
 */
public final class CommandDiscovery {

    private final JavaPlugin plugin;
    private final List<Command> sessionCommands = new ArrayList<>();

    public CommandDiscovery(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void discoverAndRegister(String basePackage) {
        SubCommandRegistry.clear();
        Set<Class<? extends AbstractCommand>> roots = new LinkedHashSet<>();
        int subCommands = 0;
        for (String className : scan(basePackage)) {
            Class<?> type = load(className);
            if (type == null || type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
                continue;
            }
            if (SubCommandHandler.class.isAssignableFrom(type) && type.isAnnotationPresent(SubCommandMeta.class)) {
                SubCommandRegistry.register(type.getAnnotation(SubCommandMeta.class).root(),
                        type.asSubclass(SubCommandHandler.class));
                subCommands++;
            } else if (AbstractCommand.class.isAssignableFrom(type) && type.isAnnotationPresent(CommandMeta.class)) {
                roots.add(type.asSubclass(AbstractCommand.class));
            }
        }
        for (Class<? extends AbstractCommand> type : roots) {
            register(type);
        }
        plugin.getLogger().info("Registered " + roots.size() + " command(s) with " + subCommands
                + " sub-command(s) from " + basePackage);
    }

    private void register(Class<? extends AbstractCommand> type) {
        try {
            Constructor<? extends AbstractCommand> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            AbstractCommand command = constructor.newInstance();
            PluginCommand declared = plugin.getCommand(command.getName());
            if (declared != null) {
                declared.setExecutor((sender, cmd, label, args) -> command.execute(sender, label, args));
                declared.setTabCompleter(command);
                return;
            }
            CommandMap commandMap = Bukkit.getCommandMap();
            commandMap.register(plugin.getName().toLowerCase(Locale.ROOT), command);
            sessionCommands.add(command);
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to register command " + type.getName(), e);
        }
    }

    /** Unregisters the commands this session put on the command map (plugin.yml commands are handled by Bukkit). */
    public void unregister() {
        CommandMap commandMap = Bukkit.getCommandMap();
        for (Command command : sessionCommands) {
            command.unregister(commandMap);
            try {
                commandMap.getKnownCommands().values().removeIf(known -> known == command);
            } catch (UnsupportedOperationException readOnlyView) {
                plugin.getLogger().fine("Command map is read-only; /" + command.getName() + " stays until restart");
            }
        }
        sessionCommands.clear();
    }

    /** Fully-qualified names of the top-level classes under {@code basePackage} inside the plugin's code source. */
    private List<String> scan(String basePackage) {
        String packagePath = basePackage.replace('.', '/') + '/';
        List<String> classes = new ArrayList<>();
        try {
            CodeSource source = plugin.getClass().getProtectionDomain().getCodeSource();
            Path location = Path.of(source.getLocation().toURI());
            if (Files.isDirectory(location)) {
                Path root = location.resolve(packagePath);
                if (Files.isDirectory(root)) {
                    try (Stream<Path> walk = Files.walk(root)) {
                        walk.map(path -> location.relativize(path).toString().replace(File.separatorChar, '/'))
                                .filter(CommandDiscovery::isTopLevelClass)
                                .forEach(entry -> classes.add(toClassName(entry)));
                    }
                }
            } else {
                try (JarFile jar = new JarFile(location.toFile())) {
                    Enumeration<JarEntry> entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        String entry = entries.nextElement().getName();
                        if (entry.startsWith(packagePath) && isTopLevelClass(entry)) {
                            classes.add(toClassName(entry));
                        }
                    }
                }
            }
        } catch (IOException | URISyntaxException | RuntimeException e) {
            throw new IllegalStateException("Failed discovering commands in package " + basePackage, e);
        }
        return classes;
    }

    private Class<?> load(String className) {
        try {
            return Class.forName(className, true, plugin.getClass().getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            plugin.getLogger().log(Level.WARNING, "Skipping unloadable class " + className, e);
            return null;
        }
    }

    private static boolean isTopLevelClass(String entry) {
        return entry.endsWith(".class") && !entry.contains("$") && !entry.endsWith("package-info.class");
    }

    private static String toClassName(String entry) {
        return entry.substring(0, entry.length() - ".class".length()).replace('/', '.');
    }
}
