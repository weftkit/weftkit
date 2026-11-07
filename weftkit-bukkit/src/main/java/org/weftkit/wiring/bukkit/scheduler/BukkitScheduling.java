package org.weftkit.wiring.bukkit.scheduler;

import java.time.Duration;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

/** The {@link Scheduler} over the Bukkit scheduler, every region the main thread. */
final class BukkitScheduling implements Scheduler {

    private static final long TICK_MILLIS = 50;

    private final Plugin plugin;

    private final Supplier<BukkitScheduler> scheduler;

    private final TaskTracker<BukkitTask> tasks;

    private final Region main = new MainThread();

    private final Async pool = new Pool();

    BukkitScheduling(Plugin plugin) {
        this(plugin, () -> plugin.getServer().getScheduler());
    }

    BukkitScheduling(Plugin plugin, Supplier<BukkitScheduler> scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.tasks = new TaskTracker<>(
                BukkitTask::cancel,
                task -> !scheduler.get().isQueued(task.getTaskId())
                        && !scheduler.get().isCurrentlyRunning(task.getTaskId()));
    }

    @Override
    public Region global() {
        return main;
    }

    @Override
    public Region at(Location location) {
        return main;
    }

    @Override
    public Region on(Entity entity) {
        return main;
    }

    @Override
    public Async async() {
        return pool;
    }

    @Override
    public void cancelAll() {
        tasks.cancelAll();
    }

    /** Rounds the duration up to whole ticks. */
    static long ticks(Duration duration) {
        long millis = Math.max(0, duration.toMillis());
        return (millis + TICK_MILLIS - 1) / TICK_MILLIS;
    }

    static long period(long periodTicks) {
        return Math.max(1, periodTicks);
    }

    private final class MainThread implements Region {

        @Override
        public Task runLater(Runnable task, long delayTicks) {
            return tasks.once(task, (body, retired) -> scheduler.get().runTaskLater(plugin, body, delayTicks));
        }

        @Override
        public Task runRepeating(Runnable task, long delayTicks, long periodTicks) {
            return tasks.repeating(
                    task,
                    (body, retired) -> scheduler.get().runTaskTimer(plugin, body, delayTicks, period(periodTicks)));
        }

        @Override
        public boolean isCurrentThread() {
            return plugin.getServer().isPrimaryThread();
        }
    }

    private final class Pool implements Async {

        @Override
        public Task runLater(Runnable task, Duration delay) {
            return tasks.once(
                    task, (body, retired) -> scheduler.get().runTaskLaterAsynchronously(plugin, body, ticks(delay)));
        }

        @Override
        public Task runRepeating(Runnable task, Duration delay, Duration period) {
            return tasks.repeating(
                    task,
                    (body, retired) -> scheduler
                            .get()
                            .runTaskTimerAsynchronously(plugin, body, ticks(delay), period(ticks(period))));
        }
    }
}
