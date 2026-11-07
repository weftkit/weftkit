package org.weftkit.wiring.bukkit.scheduler;

import org.weftkit.wiring.Loader;
import org.weftkit.wiring.WeftModule;
import org.weftkit.wiring.bukkit.BukkitModules;

/**
 * Cancels every task scheduled through the {@link Scheduler} as the graph unloads, after every
 * other unload hook. Opt out with {@code @Registry(disable = BukkitModules.SCHEDULER)}.
 */
@WeftModule(id = BukkitModules.SCHEDULER)
public final class SchedulerModule implements Loader {

    private final Scheduler scheduler;

    public SchedulerModule(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    /** The scheduler injected into the graph, ambient or provided. */
    public Scheduler scheduler() {
        return scheduler;
    }

    @Override
    public boolean load() {
        return true;
    }

    @Override
    public void unload() {
        scheduler.cancelAll();
    }
}
