package org.weftkit.wiring.folia;

import org.bukkit.plugin.Plugin;
import org.weftkit.wiring.bukkit.scheduler.Scheduler;
import org.weftkit.wiring.bukkit.scheduler.Schedulers;

/** {@link Scheduler} factories for Folia, wired through {@link FoliaModule}. */
public final class FoliaSchedulers {

    private FoliaSchedulers() {}

    /** Returns the Folia scheduler on Folia and the Bukkit scheduler everywhere else. */
    public static Scheduler detect(Plugin plugin) {
        return Schedulers.isFolia() ? folia(plugin) : Schedulers.bukkit(plugin);
    }

    /** Returns a scheduler over Folia's threaded regions API. Fails on servers without it. */
    public static Scheduler folia(Plugin plugin) {
        return new FoliaScheduling(plugin);
    }
}
