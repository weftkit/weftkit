package org.weftkit.wiring.folia;

import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.weftkit.wiring.bukkit.scheduler.Async;
import org.weftkit.wiring.bukkit.scheduler.Region;
import org.weftkit.wiring.bukkit.scheduler.Scheduler;
import org.weftkit.wiring.bukkit.scheduler.Task;
import org.weftkit.wiring.bukkit.scheduler.TaskTracker;

/** The {@link Scheduler} over Folia's threaded regions. */
final class FoliaScheduling implements Scheduler {

    private final Plugin plugin;

    private final Server server;

    private final GlobalRegionScheduler global;

    private final RegionScheduler region;

    private final AsyncScheduler async;

    private final TaskTracker<ScheduledTask> tasks =
            new TaskTracker<>(ScheduledTask::cancel, ScheduledTask::isCancelled);

    private final Region globalRegion = new Global();

    private final Async pool = new Pool();

    FoliaScheduling(Plugin plugin) {
        this(
                plugin,
                plugin.getServer(),
                plugin.getServer().getGlobalRegionScheduler(),
                plugin.getServer().getRegionScheduler(),
                plugin.getServer().getAsyncScheduler());
    }

    FoliaScheduling(
            Plugin plugin, Server server, GlobalRegionScheduler global, RegionScheduler region, AsyncScheduler async) {
        this.plugin = plugin;
        this.server = server;
        this.global = global;
        this.region = region;
        this.async = async;
    }

    @Override
    public Region global() {
        return globalRegion;
    }

    @Override
    public Region at(Location location) {
        return new At(location);
    }

    @Override
    public Region on(Entity entity) {
        return new On(entity);
    }

    @Override
    public Async async() {
        return pool;
    }

    @Override
    public void cancelAll() {
        tasks.cancelAll();
    }

    static long atLeastOne(long value) {
        return Math.max(1, value);
    }

    static long millis(Duration duration) {
        return Math.max(0, duration.toMillis());
    }

    private static Consumer<ScheduledTask> consume(Runnable body) {
        return scheduled -> body.run();
    }

    private final class Global implements Region {

        @Override
        public Task runLater(Runnable task, long delayTicks) {
            return tasks.once(
                    task,
                    (body, retired) -> delayTicks > 0
                            ? global.runDelayed(plugin, consume(body), delayTicks)
                            : global.run(plugin, consume(body)));
        }

        @Override
        public Task runRepeating(Runnable task, long delayTicks, long periodTicks) {
            return tasks.repeating(
                    task,
                    (body, retired) -> global.runAtFixedRate(
                            plugin, consume(body), atLeastOne(delayTicks), atLeastOne(periodTicks)));
        }

        @Override
        public boolean isCurrentThread() {
            return server.isGlobalTickThread();
        }
    }

    private final class At implements Region {

        private final Location location;

        At(Location location) {
            this.location = location;
        }

        @Override
        public Task runLater(Runnable task, long delayTicks) {
            return tasks.once(
                    task,
                    (body, retired) -> delayTicks > 0
                            ? region.runDelayed(plugin, location, consume(body), delayTicks)
                            : region.run(plugin, location, consume(body)));
        }

        @Override
        public Task runRepeating(Runnable task, long delayTicks, long periodTicks) {
            return tasks.repeating(
                    task,
                    (body, retired) -> region.runAtFixedRate(
                            plugin, location, consume(body), atLeastOne(delayTicks), atLeastOne(periodTicks)));
        }

        @Override
        public boolean isCurrentThread() {
            return server.isOwnedByCurrentRegion(location);
        }
    }

    private final class On implements Region {

        private final Entity entity;

        On(Entity entity) {
            this.entity = entity;
        }

        @Override
        public Task runLater(Runnable task, long delayTicks) {
            return tasks.once(
                    task,
                    (body, retired) -> delayTicks > 0
                            ? entity.getScheduler().runDelayed(plugin, consume(body), retired, delayTicks)
                            : entity.getScheduler().run(plugin, consume(body), retired));
        }

        @Override
        public Task runRepeating(Runnable task, long delayTicks, long periodTicks) {
            return tasks.repeating(
                    task,
                    (body, retired) -> entity.getScheduler()
                            .runAtFixedRate(
                                    plugin, consume(body), retired, atLeastOne(delayTicks), atLeastOne(periodTicks)));
        }

        @Override
        public boolean isCurrentThread() {
            return server.isOwnedByCurrentRegion(entity);
        }
    }

    private final class Pool implements Async {

        @Override
        public Task runLater(Runnable task, Duration delay) {
            long millis = millis(delay);
            return tasks.once(
                    task,
                    (body, retired) -> millis > 0
                            ? async.runDelayed(plugin, consume(body), millis, TimeUnit.MILLISECONDS)
                            : async.runNow(plugin, consume(body)));
        }

        @Override
        public Task runRepeating(Runnable task, Duration delay, Duration period) {
            return tasks.repeating(
                    task,
                    (body, retired) -> async.runAtFixedRate(
                            plugin, consume(body), millis(delay), atLeastOne(millis(period)), TimeUnit.MILLISECONDS));
        }
    }
}
