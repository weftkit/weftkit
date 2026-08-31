package org.weftkit.wiring.bukkit.metrics;

import org.bstats.bukkit.Metrics;
import org.bstats.charts.CustomChart;
import org.bukkit.plugin.java.JavaPlugin;
import org.weftkit.wiring.Loader;

/** The plugin's own bStats page, backing {@link PluginMetrics} while the module runs. */
final class PluginBStats implements PluginMetrics, Loader {

    private final JavaPlugin plugin;

    private final int serviceId;

    private Metrics metrics;

    PluginBStats(JavaPlugin plugin, int serviceId) {
        this.plugin = plugin;
        this.serviceId = serviceId;
    }

    @Override
    public boolean load() {
        metrics = new Metrics(plugin, serviceId);
        return true;
    }

    @Override
    public void unload() {
        if (metrics == null) return;
        metrics.shutdown();
        metrics = null;
    }

    @Override
    public void add(CustomChart chart) {
        if (metrics != null) metrics.addCustomChart(chart);
    }
}
