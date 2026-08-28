package org.weftkit.wiring.bukkit.metrics;

import java.util.logging.Level;
import org.bukkit.plugin.java.JavaPlugin;
import org.weftkit.wiring.ModuleLifecycle;
import org.weftkit.wiring.WeftModule;
import org.weftkit.wiring.bukkit.modules.BukkitModules;

/**
 * Runs weftkit's own anonymous bStats reporting, see {@link WeftkitMetrics}. Configured through
 * {@link MetricsSettings}. Opt out there, by excluding org.bstats from the jar, or with
 * {@code @Registry(disable = BukkitModules.METRICS)}.
 */
@WeftModule(id = BukkitModules.METRICS)
public final class MetricsModule implements ModuleLifecycle {

    private final JavaPlugin plugin;

    private final MetricsSettings settings;

    private WeftkitMetrics metrics;

    public MetricsModule(JavaPlugin plugin, MetricsSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    // Metrics must never take a plugin down. Missing bStats classes mean the consumer stripped
    // them from its jar to opt out, so both failure kinds only log quietly
    @Override
    public boolean activate() {
        try {
            if (!settings.enabled()) return true;
            WeftkitMetrics started = new WeftkitMetrics(plugin, settings.reportName());
            started.load();
            metrics = started;
        } catch (RuntimeException | LinkageError ex) {
            plugin.getLogger().log(Level.FINE, "weftkit metrics unavailable", ex);
        }
        return true;
    }

    @Override
    public void deactivate() {
        if (metrics == null) return;
        try {
            metrics.unload();
        } catch (RuntimeException | LinkageError ex) {
            plugin.getLogger().log(Level.FINE, "weftkit metrics teardown failed", ex);
        } finally {
            metrics = null;
        }
    }
}
