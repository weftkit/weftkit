package org.weftkit.wiring.bukkit.metrics;

import org.bstats.charts.CustomChart;

/**
 * The plugin's own bStats page, live while the {@code bukkit:metrics} module runs with a
 * {@link MetricsSettings#serviceId()}. Handed to every {@link MetricsContributor}; bStats charts
 * pull from their suppliers, so registering once is the whole lifecycle.
 */
public interface PluginMetrics {

    /** Registers a chart on the plugin's bStats page. */
    void add(CustomChart chart);
}
