package org.weftkit.wiring.bukkit.metrics;

import org.weftkit.wiring.ModuleSettings;

/**
 * Consumer configuration for weftkit's own metrics reporting. Optionally wire one implementation
 * as a normal component, backed by whatever you like (Bukkit's config, a constant); without one
 * the defaults below apply.
 */
@ModuleSettings
public interface MetricsSettings {

    /** Whether weftkit's anonymous bStats reporting runs for this plugin. */
    default boolean enabled() {
        return true;
    }

    /** Whether the plugin's name appears on weftkit's public bStats chart. */
    default boolean reportName() {
        return false;
    }
}
