package org.weftkit.wiring.bukkit.modules;

import org.weftkit.wiring.bukkit.listeners.ListenerModule;
import org.weftkit.wiring.bukkit.metrics.MetricsModule;

/**
 * The ids of the modules bundled with the Bukkit adapter. Use the constants in
 * {@code @Registry(disable = ...)}: the holder imports no module class and the compiler inlines
 * the constant, so an opt-out is typo-safe and carries no trace of the disabled module.
 */
public final class BukkitModules {

    /** The server listener registration of {@link ListenerModule}. */
    public static final String LISTENERS = "bukkit:listeners";

    /** weftkit's own anonymous bStats reporting, {@link MetricsModule}. */
    public static final String METRICS = "bukkit:metrics";

    private BukkitModules() {}
}
