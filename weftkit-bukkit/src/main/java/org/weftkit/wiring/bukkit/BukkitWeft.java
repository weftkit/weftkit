package org.weftkit.wiring.bukkit;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.weftkit.wiring.bukkit.metrics.WeftkitMetrics;
import org.weftkit.wiring.runtime.ComponentRegistry;
import org.weftkit.wiring.runtime.WeftLoader;

/**
 * The Bukkit entry points for weftkit: {@link #enable} from {@code onEnable} and {@link #disable}
 * from {@code onDisable}.
 */
public final class BukkitWeft {

    private static final Map<String, WeftkitMetrics> METRICS = new HashMap<>();

    private BukkitWeft() {}

    /**
     * Loads the registry with the plugin and the extra values as ambient roots, then registers
     * every wired {@code Listener} and starts weftkit's own anonymous bStats reporting, see
     * {@link WeftkitMetrics}. Returns null after disabling the plugin when a component aborts or
     * fails the load. A load failure is logged instead of escaping {@code onEnable}, where Bukkit
     * would only log it and leave the plugin half-enabled.
     */
    public static WeftLoader enable(JavaPlugin plugin, ComponentRegistry registry, Object... extraAmbient) {
        WeftLoader loader = new WeftLoader(registry, ambient(plugin, extraAmbient));
        boolean loaded;
        try {
            loaded = loader.load();
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error during weftkit startup", ex);
            loaded = false;
        }
        if (!loaded) {
            plugin.getServer().getPluginManager().disablePlugin(plugin);
            return null;
        }
        registerListeners(plugin, loader);
        startMetrics(plugin);
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
        stopMetrics(plugin);
        if (loader == null) return;
        try {
            loader.unload();
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error during weftkit shutdown", ex);
        }
    }

    // Metrics must never take a plugin down. Missing bStats classes mean the consumer stripped
    // them from its jar to opt out, so both failure kinds only log quietly
    private static void startMetrics(JavaPlugin plugin) {
        if (WeftkitMetrics.optedOut(plugin.getClass())) return;
        try {
            WeftkitMetrics metrics = new WeftkitMetrics(plugin);
            metrics.load();
            METRICS.put(plugin.getName(), metrics);
        } catch (RuntimeException | LinkageError ex) {
            plugin.getLogger().log(Level.FINE, "weftkit metrics unavailable", ex);
        }
    }

    private static void stopMetrics(JavaPlugin plugin) {
        WeftkitMetrics metrics = METRICS.remove(plugin.getName());
        if (metrics == null) return;
        try {
            metrics.unload();
        } catch (RuntimeException | LinkageError ex) {
            plugin.getLogger().log(Level.FINE, "weftkit metrics teardown failed", ex);
        }
    }

    private static Object[] ambient(JavaPlugin plugin, Object[] extraAmbient) {
        Object[] ambient = new Object[extraAmbient.length + 1];
        ambient[0] = plugin;
        System.arraycopy(extraAmbient, 0, ambient, 1, extraAmbient.length);
        return ambient;
    }
}
