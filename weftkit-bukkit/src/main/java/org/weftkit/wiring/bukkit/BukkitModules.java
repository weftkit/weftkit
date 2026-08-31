package org.weftkit.wiring.bukkit;

import org.weftkit.wiring.bukkit.commands.CommandModule;
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

    /** The command binding of {@link CommandModule}. */
    public static final String COMMANDS = "bukkit:commands";

    /** weftkit's own anonymous bStats reporting, {@link MetricsModule}. */
    public static final String METRICS = "bukkit:metrics";

    private BukkitModules() {}
}
