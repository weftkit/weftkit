package org.weftkit.wiring.bukkit.metrics;

/**
 * Implemented by wired components that publish charts to the plugin's own bStats page. Collected
 * through {@code Weft} once the graph is up, feature flagged ones excluded, and only called when
 * the {@code bukkit:metrics} module actually starts plugin metrics, so implementations never
 * check availability themselves.
 */
public interface MetricsContributor {

    /** Registers this component's charts. Called once, after the graph is up. */
    void contribute(PluginMetrics metrics);
}
