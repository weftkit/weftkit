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
            Supplier<ComponentRegistry> registry,
            Supplier<Scheduler> scheduler,
            Supplier<Object[]> ambient,
            Consumer<WeftLoader> hook) {
        try {
            loader = BukkitWeft.enableWith(plugin, registry.get(), scheduler, ambient.get());
            if (loader == null) return;
            hook.accept(loader);
        } catch (RuntimeException | Error ex) {
            plugin.getLogger().log(Level.SEVERE, "Error during plugin startup", ex);
            try {
                disable(ignored -> {});
            } catch (RuntimeException | Error teardown) {
                if (ex != teardown) ex.addSuppressed(teardown);
            } finally {
                plugin.getServer().getPluginManager().disablePlugin(plugin);
            }
            if (ex instanceof Error error) throw error;
        }
    }

    void disable(Consumer<WeftLoader> hook) {
        WeftLoader current = loader;
        if (current == null || disabling) return;
        disabling = true;
        Throwable failure = null;
        try {
            hook.accept(current);
        } catch (RuntimeException | Error ex) {
            failure = ex;
            throw ex;
        } finally {
            try {
                BukkitWeft.disable(plugin, current);
            } catch (RuntimeException | Error teardown) {
                if (failure == null) throw teardown;
                if (failure != teardown) failure.addSuppressed(teardown);
            } finally {
                loader = null;
                disabling = false;
            }
        }
    }
}
