package org.weftkit.wiring.runtime;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Map helpers for generated {@link ComponentRegistry} implementations. A build's central registry
 * merges the wiring fragments generated into packages with non-public components through these
 * methods. They hold no state and grant nothing beyond the maps handed to them.
 */
public final class Registries {

    private Registries() {}

    /** Returns an immutable map holding the entries with every contribution merged in. */
    @SafeVarargs
    public static <V> Map<Class<?>, V> merge(Map<Class<?>, V> entries, Map<Class<?>, V>... contributions) {
        Map<Class<?>, V> merged = new HashMap<>(entries);
        for (Map<Class<?>, V> contribution : contributions) merged.putAll(contribution);
        return Map.copyOf(merged);
    }

    /** Returns an immutable two-level map with the contributions merged per outer key. */
    @SafeVarargs
    public static <V> Map<Class<?>, Map<String, V>> mergeNested(
            Map<Class<?>, Map<String, V>> entries, Map<Class<?>, Map<String, V>>... contributions) {
        Map<Class<?>, Map<String, V>> merged = new HashMap<>(entries);
        for (Map<Class<?>, Map<String, V>> contribution : contributions)
            contribution.forEach((type, qualified) -> merged.merge(type, qualified, (base, addition) -> {
                Map<String, V> combined = new HashMap<>(base);
                combined.putAll(addition);
                return Map.copyOf(combined);
            }));
        return Map.copyOf(merged);
    }

    /** Returns an immutable set holding the entries with every contribution merged in. */
    @SafeVarargs
    public static Set<Class<?>> union(Set<Class<?>> entries, Set<Class<?>>... contributions) {
        Set<Class<?>> merged = new HashSet<>(entries);
        for (Set<Class<?>> contribution : contributions) merged.addAll(contribution);
        return Set.copyOf(merged);
    }

    /**
     * Returns the class with the binary name from the components map. The generated registry
     * references non-public components this way, so a miss means the registry and a package
     * fragment were generated from different sources.
     */
    public static Class<?> type(Map<Class<?>, ?> components, String name) {
        for (Class<?> type : components.keySet()) {
            if (type.getName().equals(name)) return type;
        }
        throw new IllegalStateException("Stale weftkit registry, run a clean build: " + name);
    }
}
