package org.weftkit.wiring.bukkit;

import java.util.logging.Level;
import org.bukkit.command.CommandExecutor;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.weftkit.wiring.registry.ComponentRegistry;
import org.weftkit.wiring.runtime.Modules;
import org.weftkit.wiring.runtime.WeftLoader;

/**
 * The Bukkit entry points for weftkit: {@link #enable} from {@code onEnable} and {@link #disable}
 * from {@code onDisable}.
 */
public final class BukkitWeft {

    private BukkitWeft() {}

    /**
     * Loads the registry with the plugin and the extra values as ambient roots, each extra value
     * matching a declared ambient type, then activates its modules. On any abort or failure the
     * graph is torn down, the plugin is disabled, and null is returned. Failures are logged
     * instead of escaping {@code onEnable}.
     */
    public static WeftLoader enable(JavaPlugin plugin, ComponentRegistry registry, Object... extraAmbient) {
        WeftLoader loader = new WeftLoader(registry, ambient(plugin, extraAmbient));
        try {
            if (!loader.load()) return abort(plugin, null);
            if (registry.modules().isEmpty() && hasListenersOrCommands(registry))
                plugin.getLogger()
                        .warning("The registry contains no weftkit modules, so the wired listeners and commands will"
                                + " not be registered. Is weftkit-bukkit missing from the annotationProcessor path?");
            if (!Modules.activate(loader)) return abort(plugin, loader);
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error during weftkit startup", ex);
            // A load failure already unloaded, an activation failure has not; unload is idempotent
            return abort(plugin, loader);
        }
        return loader;
    }

    /**
     * Deactivates the modules and unloads the graph, logging teardown failures through the plugin
     * logger so shutdown never escapes {@code onDisable}. Accepts null for an aborted enable.
     */
    public static void disable(JavaPlugin plugin, WeftLoader loader) {
        if (loader == null) return;
        RuntimeException failure = null;
        try {
            Modules.deactivate(loader);
        } catch (RuntimeException ex) {
            failure = ex;
        }
        try {
            loader.unload();
        } catch (RuntimeException ex) {
            if (failure == null) failure = ex;
            else failure.addSuppressed(ex);
        }
        if (failure != null) plugin.getLogger().log(Level.SEVERE, "Error during weftkit shutdown", failure);
    }

    // Wired listeners or commands with no modules: weftkit-bukkit missing from the processor path,
    // or all modules disabled on purpose; either way they stay unregistered
    private static boolean hasListenersOrCommands(ComponentRegistry registry) {
        return registry.parameters().keySet().stream()
                .anyMatch(
                        type -> Listener.class.isAssignableFrom(type) || CommandExecutor.class.isAssignableFrom(type));
    }

    private static WeftLoader abort(JavaPlugin plugin, WeftLoader loader) {
        if (loader != null) {
            try {
                loader.unload();
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.SEVERE, "Error during weftkit teardown", ex);
            }
        }
        plugin.getServer().getPluginManager().disablePlugin(plugin);
        return null;
    }

    private static Object[] ambient(JavaPlugin plugin, Object[] extraAmbient) {
        Object[] ambient = new Object[extraAmbient.length + 1];
        ambient[0] = plugin;
        System.arraycopy(extraAmbient, 0, ambient, 1, extraAmbient.length);
        return ambient;
    }
}
