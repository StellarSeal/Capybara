package me.stella.command.dispatch;

import me.stella.command.suggestion.SubCommandHandler;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Sub-command classes grouped by the root command they belong to; filled by {@link CommandDiscovery}. */
public final class SubCommandRegistry {

    private static final Map<String, Set<Class<? extends SubCommandHandler>>> REGISTRY = new ConcurrentHashMap<>();

    private SubCommandRegistry() {
    }

    public static void register(String root, Class<? extends SubCommandHandler> type) {
        REGISTRY.computeIfAbsent(root.toLowerCase(Locale.ROOT), k -> ConcurrentHashMap.newKeySet()).add(type);
    }

    public static Set<Class<? extends SubCommandHandler>> get(String root) {
        return Collections.unmodifiableSet(REGISTRY.getOrDefault(root.toLowerCase(Locale.ROOT), Set.of()));
    }

    public static void clear() {
        REGISTRY.clear();
    }
}
