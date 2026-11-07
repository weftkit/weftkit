package org.weftkit.wiring.folia;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.Test;
import org.weftkit.wiring.bukkit.scheduler.Async;
import org.weftkit.wiring.bukkit.scheduler.Task;
import org.weftkit.wiring.bukkit.scheduler.TaskScope;
import org.weftkit.wiring.folia.FoliaStubs.Recording;

class FoliaSchedulingTest {

    @Test
    void taskScopesPreserveFoliaRoutingAndEntityRetirement() {
        TaskScope scope = scheduler.scope();
        Task globalTask = scope.global().run(task);
        Task regionTask = scope.at(location).runRepeating(task, 2, 3);
        Task entityTask = scope.on(entity).runLater(task, 4);
        Task asyncTask = scope.async().run(task);
        Task parentTask = scheduler.global().run(task);
        assertEquals("run", global.calls.get(0).method());
        assertArrayEquals(new Object[] {location, 2L, 3L}, region.calls.get(0).args());
        assertEquals("runDelayed", entityScheduler.calls.get(0).method());
        assertEquals("runNow", async.calls.get(0).method());
        entityScheduler.retireHooks.get(0).run();
        assertTrue(entityTask.isCancelled());
        scope.close();
        assertTrue(globalTask.isCancelled());
        assertTrue(regionTask.isCancelled());
        assertTrue(asyncTask.isCancelled());
        assertFalse(parentTask.isCancelled());
        scheduler.close();
    }

    private final Recording global = new Recording();

    private final Recording region = new Recording();

    private final Recording async = new Recording();

    private final Recording entityScheduler = new Recording();

    private final Entity entity = FoliaStubs.entity(entityScheduler.as(EntityScheduler.class));

    private final Location location = new Location(null, 1, 2, 3);

    private final FoliaScheduling scheduler = new FoliaScheduling(
            FoliaStubs.plugin(null),
            null,
            global.as(GlobalRegionScheduler.class),
            region.as(RegionScheduler.class),
            async.as(AsyncScheduler.class));

    private final Runnable task = () -> {};

    @Test
    void teardownClosesAllSchedulingRoutesIncludingRetainedRegions() {
        org.weftkit.wiring.bukkit.scheduler.Region retained = scheduler.at(location);
        Task scheduled = scheduler.global().run(task);
        scheduler.close();
        assertTrue(scheduled.isCancelled());
        assertThrows(IllegalStateException.class, () -> retained.run(task));
        assertThrows(IllegalStateException.class, () -> scheduler.global().runRepeating(task, 1, 1));
        assertThrows(IllegalStateException.class, () -> scheduler.on(entity).run(task));
        assertThrows(IllegalStateException.class, () -> scheduler.async().run(task));
        scheduler.close();
    }

    @Test
    void aZeroDelayRunsOnTheNextTick() {
        scheduler.global().run(task);
        scheduler.at(location).runLater(task, 0);
        scheduler.on(entity).run(task);
        assertEquals("run", global.calls.get(0).method());
        assertEquals("run", region.calls.get(0).method());
        assertEquals("run", entityScheduler.calls.get(0).method());
    }

    @Test
    void aPositiveDelayIsPassedThrough() {
        scheduler.global().runLater(task, 4);
        scheduler.at(location).runLater(task, 5);
        scheduler.on(entity).runLater(task, 6);
        assertEquals("runDelayed", global.calls.get(0).method());
        assertArrayEquals(new Object[] {4L}, global.calls.get(0).args());
        assertArrayEquals(new Object[] {location, 5L}, region.calls.get(0).args());
        assertArrayEquals(new Object[] {6L}, entityScheduler.calls.get(0).args());
    }

    @Test
    void repeatingTasksStartAndRepeatAtLeastOneTickApart() {
        scheduler.global().runRepeating(task, 0, 0);
        scheduler.at(location).runRepeating(task, 0, 7);
        scheduler.on(entity).runRepeating(task, 3, 0);
        assertEquals("runAtFixedRate", global.calls.get(0).method());
        assertArrayEquals(new Object[] {1L, 1L}, global.calls.get(0).args());
        assertArrayEquals(new Object[] {location, 1L, 7L}, region.calls.get(0).args());
        assertArrayEquals(new Object[] {3L, 1L}, entityScheduler.calls.get(0).args());
    }

    @Test
    void asyncTasksUseMilliseconds() {
        Async pool = scheduler.async();
        pool.run(task);
        pool.runLater(task, Duration.ZERO);
        pool.runLater(task, Duration.ofSeconds(2));
        pool.runRepeating(task, Duration.ZERO, Duration.ZERO);
        assertEquals("runNow", async.calls.get(0).method());
        assertEquals("runNow", async.calls.get(1).method());
        assertEquals("runDelayed", async.calls.get(2).method());
        assertArrayEquals(
                new Object[] {2000L, TimeUnit.MILLISECONDS}, async.calls.get(2).args());
        assertEquals("runAtFixedRate", async.calls.get(3).method());
        assertArrayEquals(
                new Object[] {0L, 1L, TimeUnit.MILLISECONDS}, async.calls.get(3).args());
    }

    @Test
    void aTaskForARemovedEntityComesBackCancelled() {
        entityScheduler.retired = true;
        Task handle = scheduler.on(entity).run(task);
        assertTrue(handle.isCancelled());
        scheduler.cancelAll();
        assertTrue(entityScheduler.cancels.isEmpty());
    }

    @Test
    void cancelAllCancelsEveryHandleAndNoPlatformWideTasks() {
        Task onRegion = scheduler.at(location).run(task);
        Task onEntity = scheduler.on(entity).runRepeating(task, 1, 1);
        scheduler.cancelAll();
        assertTrue(onRegion.isCancelled());
        assertTrue(onEntity.isCancelled());
        assertEquals(1, region.cancels.get(0)[0]);
        assertEquals(1, entityScheduler.cancels.get(0)[0]);
        assertEquals(0, global.cancelTasksCalls);
        assertEquals(0, async.cancelTasksCalls);
    }

    @Test
    void aTaskCancelledByTheSchedulerReportsCancelled() {
        Task handle = scheduler.at(location).runRepeating(task, 1, 1);
        assertFalse(handle.isCancelled());
        region.cancels.get(0)[0]++;
        assertTrue(handle.isCancelled());
    }

    @Test
    void aTaskCancelledWhileBeingScheduledIsCancelledOnceScheduled() {
        region.duringSchedule = scheduler::cancelAll;
        Task handle = scheduler.at(location).runRepeating(task, 1, 1);
        assertTrue(handle.isCancelled());
        assertEquals(1, region.cancels.get(0)[0]);
    }

    @Test
    void aFinishedOneShotTaskLeavesTheSet() {
        Task once = scheduler.global().run(task);
        global.bodies.get(0).accept(null);
        scheduler.cancelAll();
        assertFalse(once.isCancelled());
        assertEquals(0, global.cancels.get(0)[0]);
    }

    @Test
    void entityTasksRegisterARetiredAction() {
        scheduler.on(entity).run(task);
        scheduler.on(entity).runLater(task, 2);
        scheduler.on(entity).runRepeating(task, 1, 1);
        assertEquals(3, entityScheduler.retireHooks.size());
    }

    @Test
    void aTaskRetiredWithItsEntityLeavesTheSetCancelled() {
        Task handle = scheduler.on(entity).runLater(task, 5);
        entityScheduler.retireHooks.get(0).run();
        assertTrue(handle.isCancelled());
        scheduler.cancelAll();
        assertEquals(0, entityScheduler.cancels.get(0)[0]);
    }
}
