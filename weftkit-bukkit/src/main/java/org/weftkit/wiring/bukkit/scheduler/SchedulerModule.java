package org.weftkit.wiring.bukkit.scheduler;

import org.weftkit.wiring.Loader;
import org.weftkit.wiring.Provides;
import org.weftkit.wiring.Singleton;
import org.weftkit.wiring.WeftModule;
import org.weftkit.wiring.Wired;
import org.weftkit.wiring.bukkit.BukkitModules;

/**
 * Closes the scheduler and cancels every task scheduled through the {@link Scheduler} as the graph unloads, after every
 * other unload hook. Opt out with {@code @Registry(disable = BukkitModules.SCHEDULER)}.
 */
@WeftModule(id = BukkitModules.SCHEDULER, components = SchedulerModule.TaskScopes.class)
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
        scheduler.close();
    }

    /** Supplies a separate task scope for each consuming singleton. */
    @Wired
    @Singleton
    public static final class TaskScopes {
        private final Scheduler scheduler;

        public TaskScopes(Scheduler scheduler) {
            this.scheduler = scheduler;
        }

        @Provides(owned = true)
        public TaskScope taskScope() {
            return scheduler.scope();
        }
    }
}
