package org.weftkit.wiring.bukkit.metrics;

import org.weftkit.wiring.ModuleSettings;

/**
 * Consumer configuration for the {@code bukkit:metrics} module. Optionally wire one
 * implementation as a normal component, backed by whatever you like (Bukkit's config, a
 * constant); without one the defaults below apply. The module only acts when the plugin jar
 * ships bStats, which weftkit no longer bundles.
 */
@ModuleSettings
public interface MetricsSettings {

    /** The plugin's own bStats service id, fed to {@link MetricsContributor}s. 0 keeps plugin metrics off. */
    default int serviceId() {
        return 0;
    }

    /** Whether weftkit's anonymous bStats reporting rides along when the jar ships bStats. */
    default boolean enabled() {
        return true;
    }

    /** Whether the plugin's name appears on weftkit's public bStats chart. */
    default boolean reportName() {
        return false;
    }
}
