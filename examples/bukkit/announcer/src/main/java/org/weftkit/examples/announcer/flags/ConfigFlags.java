package org.weftkit.examples.announcer.flags;

import org.bukkit.plugin.java.JavaPlugin;
import org.weftkit.wiring.Loader;
import org.weftkit.wiring.Singleton;
import org.weftkit.wiring.Wired;
import org.weftkit.wiring.FeatureFlags;

/** Resolves feature flags from config.yml. A flag missing there counts as off. */
@Wired
@Singleton
final class ConfigFlags implements FeatureFlags, Loader {

    private final JavaPlugin plugin;

    ConfigFlags(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean load() {
        plugin.saveDefaultConfig();
        return true;
    }

    @Override
    public boolean isEnabled(String key) {
        return plugin.getConfig().getBoolean("flags." + key);
    }
}
