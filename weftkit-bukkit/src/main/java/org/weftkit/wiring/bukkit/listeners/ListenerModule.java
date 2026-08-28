package org.weftkit.wiring.bukkit.listeners;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.weftkit.wiring.ModuleLifecycle;
import org.weftkit.wiring.WeftModule;
import org.weftkit.wiring.bukkit.modules.BukkitModules;
import org.weftkit.wiring.runtime.Weft;

/**
 * Registers every wired {@code Listener} with the server once the graph is up, feature flagged
 * ones excluded, and unregisters exactly those on deactivation. Opt out with
 * {@code @Registry(disable = BukkitModules.LISTENERS)}.
 */
@WeftModule(id = BukkitModules.LISTENERS)
public final class ListenerModule implements ModuleLifecycle {

    private final JavaPlugin plugin;

    private final Weft weft;

    private final List<Listener> registered = new ArrayList<>();

    public ListenerModule(JavaPlugin plugin, Weft weft) {
        this.plugin = plugin;
        this.weft = weft;
    }

    @Override
    public boolean activate() {
        PluginManager manager = plugin.getServer().getPluginManager();
        for (Listener listener : weft.all(Listener.class)) {
            manager.registerEvents(listener, plugin);
            registered.add(listener);
        }
        return true;
    }

    @Override
    public void deactivate() {
        for (Listener listener : registered) HandlerList.unregisterAll(listener);
        registered.clear();
    }
}
