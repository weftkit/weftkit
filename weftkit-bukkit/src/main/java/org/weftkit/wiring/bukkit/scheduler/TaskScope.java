package org.weftkit.wiring.bukkit.scheduler;

import java.time.Duration;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

/** A child scheduler that cancels only its own tasks. Closing never closes the parent scheduler. */
public final class TaskScope implements Scheduler, AutoCloseable {
    private final Scheduler parent;
    private final TaskTracker<Task> tasks = new TaskTracker<>(Task::cancel, Task::isCancelled);
    private final Region global;
    private final Async async;

    TaskScope(Scheduler parent) {
        this.parent = parent;
        this.global = new ScopedRegion(parent.global());
        this.async = new ScopedAsync(parent.async());
    }

    @Override
    public Region global() {
        return global;
    }

    @Override
    public Region at(Location location) {
        return new ScopedRegion(parent.at(location));
    }

    @Override
    public Region on(Entity entity) {
        return new ScopedRegion(parent.on(entity));
    }

    @Override
    public Async async() {
        return async;
    }

    @Override
    public void cancelAll() {
        tasks.cancelAll();
    }

    /** Rejects new work and cancels pending tasks. Already-running callbacks may finish. */
    @Override
    public void close() {
        tasks.close();
    }

    private final class ScopedRegion implements Region {
        private final Region region;

        private ScopedRegion(Region region) {
            this.region = region;
        }

        @Override
        public Task runLater(Runnable task, long delayTicks) {
            return tasks.once(task, (body, retired) -> region.runLater(body, delayTicks));
        }

        @Override
        public Task runRepeating(Runnable task, long delayTicks, long periodTicks) {
            return tasks.repeating(task, (body, retired) -> region.runRepeating(body, delayTicks, periodTicks));
        }

        @Override
        public boolean isCurrentThread() {
            return region.isCurrentThread();
        }
    }

    private final class ScopedAsync implements Async {
        private final Async pool;

        private ScopedAsync(Async pool) {
            this.pool = pool;
        }

        @Override
        public Task runLater(Runnable task, Duration delay) {
            return tasks.once(task, (body, retired) -> pool.runLater(body, delay));
        }

        @Override
        public Task runRepeating(Runnable task, Duration delay, Duration period) {
            return tasks.repeating(task, (body, retired) -> pool.runRepeating(body, delay, period));
        }
    }
}
