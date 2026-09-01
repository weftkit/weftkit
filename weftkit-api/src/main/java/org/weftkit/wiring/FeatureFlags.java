package org.weftkit.wiring;

/**
 * Resolves whether a named {@link FeatureFlag} is on. A plugin provides one implementation as a
 * normal wired component, backed by whatever it likes (Bukkit's config, a database, a config
 * module). When no implementation is present in the graph, every flag falls back to its
 * {@link FeatureFlag#enabledByDefault}, so the mechanism is entirely opt-in.
 */
@FunctionalInterface
public interface FeatureFlags {

    /** Returns whether the flag with the given key is currently enabled. */
    boolean isEnabled(String key);
}
