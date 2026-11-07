package org.weftkit.wiring.bukkit;

import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import org.bukkit.plugin.java.JavaPlugin;
import org.weftkit.wiring.bukkit.scheduler.Scheduler;
import org.weftkit.wiring.registry.ComponentRegistry;
import org.weftkit.wiring.runtime.WeftLoader;

/** Owns the plugin's graph, including cleanup when a plugin lifecycle hook fails. */
final class PluginLifecycle {

    private final JavaPlugin plugin;
    private WeftLoader loader;

    private boolean disabling;

    PluginLifecycle(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    WeftLoader loader() {
        return loader;
    }

    void enable(
            ComponentRegistry registry, Supplier<Scheduler> scheduler, Object[] ambient, Consumer<WeftLoader> hook) {
        loader = BukkitWeft.enableWith(plugin, registry, scheduler, ambient);
        if (loader == null) return;
        try {
            hook.accept(loader);
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error during plugin enable hook", ex);
            disable(ignored -> {});
            plugin.getServer().getPluginManager().disablePlugin(plugin);
        }
    }

    void disable(Consumer<WeftLoader> hook) {
        WeftLoader current = loader;
        if (current == null || disabling) return;
        disabling = true;
        try {
            hook.accept(current);
        } finally {
            try {
                BukkitWeft.disable(plugin, current);
            } finally {
                loader = null;
                disabling = false;
            }
        }
    }
}
