package org.weftkit.wiring.bukkit;

import java.util.logging.Level;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.weftkit.wiring.runtime.ComponentRegistry;
import org.weftkit.wiring.runtime.WeftLoader;

/**
 * The Bukkit entry points for weftkit: {@link #enable} from {@code onEnable} and {@link #disable}
 * from {@code onDisable}.
 */
public final class BukkitWeft {

    private BukkitWeft() {}

    /**
     * Loads the registry with the plugin and the extra values as ambient roots, then registers
     * every wired {@code Listener}. Returns null after disabling the plugin when a component
     * aborts the load.
     */
    public static WeftLoader enable(JavaPlugin plugin, ComponentRegistry registry, Object... extraAmbient) {
        WeftLoader loader = new WeftLoader(registry, ambient(plugin, extraAmbient));
        if (!loader.load()) {
            plugin.getServer().getPluginManager().disablePlugin(plugin);
            return null;
        }
        registerListeners(plugin, loader);
        return loader;
    }

    /** Registers every wired {@code Listener} component with the server. */
    public static void registerListeners(JavaPlugin plugin, WeftLoader loader) {
        PluginManager manager = plugin.getServer().getPluginManager();
        loader.createAll(Listener.class).forEach(listener -> manager.registerEvents(listener, plugin));
    }

    /**
     * Unloads the loader, logging teardown failures through the plugin logger so shutdown never
     * escapes {@code onDisable}. Accepts null for a plugin whose enable was aborted.
     */
    public static void disable(JavaPlugin plugin, WeftLoader loader) {
        if (loader == null) return;
        try {
            loader.unload();
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error during weftkit shutdown", ex);
        }
    }

    private static Object[] ambient(JavaPlugin plugin, Object[] extraAmbient) {
        Object[] ambient = new Object[extraAmbient.length + 1];
        ambient[0] = plugin;
        System.arraycopy(extraAmbient, 0, ambient, 1, extraAmbient.length);
        return ambient;
    }
}
