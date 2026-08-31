package org.weftkit.wiring.bukkit.metrics;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.plugin.java.JavaPlugin;
import org.weftkit.wiring.ModuleLifecycle;
import org.weftkit.wiring.WeftModule;
import org.weftkit.wiring.bukkit.BukkitModules;
import org.weftkit.wiring.runtime.Weft;

/**
 * Runs bStats metrics when the plugin jar ships bStats, which weftkit does not bundle: the
 * plugin's own charts through {@link MetricsContributor}s once {@link MetricsSettings#serviceId()}
 * is set, and weftkit's anonymous reporting, see {@link WeftkitMetrics}. Without bStats on the
 * classpath the module is inert. Opt out of the reporting through {@link MetricsSettings} or with
 * {@code @Registry(disable = BukkitModules.METRICS)}.
 */
@WeftModule(id = BukkitModules.METRICS)
public final class MetricsModule implements ModuleLifecycle {

    private final JavaPlugin plugin;

    private final MetricsSettings settings;

    private final Weft weft;

    private WeftkitMetrics metrics;

    private PluginBStats pluginMetrics;

    public MetricsModule(JavaPlugin plugin, MetricsSettings settings, Weft weft) {
        this.plugin = plugin;
        this.settings = settings;
        this.weft = weft;
    }

    @Override
    public boolean activate() {
        if (settings.enabled()) guarded(Level.FINE, this::startWeftkit, "weftkit metrics unavailable");
        if (settings.serviceId() > 0) guarded(Level.WARNING, this::startPlugin, "Plugin metrics unavailable");
        return true;
    }

    @Override
    public void deactivate() {
        if (metrics != null) {
            guarded(Level.FINE, metrics::unload, "weftkit metrics teardown failed");
            metrics = null;
        }
        if (pluginMetrics != null) {
            guarded(Level.FINE, pluginMetrics::unload, "plugin metrics teardown failed");
            pluginMetrics = null;
        }
    }

    private void startWeftkit() {
        WeftkitMetrics started = new WeftkitMetrics(plugin, settings.reportName());
        started.load();
        metrics = started;
    }

    private void startPlugin() {
        PluginBStats started = new PluginBStats(plugin, settings.serviceId());
        started.load();
        pluginMetrics = started;
        contributeAll(started, weft.all(MetricsContributor.class), plugin.getLogger());
    }

    // One broken contributor must not silence the others
    static void contributeAll(PluginMetrics metrics, Iterable<MetricsContributor> contributors, Logger logger) {
        for (MetricsContributor contributor : contributors) {
            try {
                contributor.contribute(metrics);
            } catch (RuntimeException ex) {
                logger.log(
                        Level.WARNING,
                        "Metrics contributor " + contributor.getClass().getName() + " failed",
                        ex);
            }
        }
    }

    // Metrics must never take a plugin down, and a jar without bStats classes is the normal
    // no-metrics state, so failures only log
    private void guarded(Level level, Runnable step, String failure) {
        try {
            step.run();
        } catch (RuntimeException | LinkageError ex) {
            plugin.getLogger().log(level, failure, ex);
        }
    }
}
