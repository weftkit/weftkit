package org.weftkit.wiring.bukkit.scheduler;

import org.bukkit.plugin.Plugin;

/** Factories and platform probes for the {@link Scheduler} implementations of the Bukkit adapter. */
public final class Schedulers {

    private static final boolean FOLIA = probeFolia();

    private Schedulers() {}

    /** A scheduler over the Bukkit scheduler. */
    public static Scheduler bukkit(Plugin plugin) {
        return new BukkitScheduling(plugin);
    }

    /** Whether the server is Folia. */
    public static boolean isFolia() {
        return FOLIA;
    }

    private static boolean probeFolia() {
        try {
            Class.forName(
                    "io.papermc.paper.threadedregions.RegionizedServer", false, Schedulers.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError ex) {
            return false;
        }
    }
}
