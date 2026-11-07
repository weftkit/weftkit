package org.weftkit.wiring.folia;

import org.bukkit.plugin.java.JavaPlugin;
import org.weftkit.wiring.Provides;
import org.weftkit.wiring.Singleton;
import org.weftkit.wiring.Wired;
import org.weftkit.wiring.bukkit.scheduler.Scheduler;

/** Provides the {@code Scheduler} of {@link FoliaSchedulers#detect}. */
@Wired
@Singleton
public final class SchedulerDetection {

    private final Scheduler scheduler;

    public SchedulerDetection(JavaPlugin plugin) {
        this(FoliaSchedulers.detect(plugin));
    }

    SchedulerDetection(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Provides
    public Scheduler scheduler() {
        return scheduler;
    }
}
