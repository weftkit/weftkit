package org.weftkit.wiring.bukkit.scheduler;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;
import org.weftkit.wiring.bukkit.scheduler.SchedulerStubs.RecordingScheduler;

class BukkitSchedulingTest {

    private final RecordingScheduler bukkit = new RecordingScheduler();

    private final BukkitScheduling scheduler = new BukkitScheduling(SchedulerStubs.plugin(true), bukkit::scheduler);

    private final Location location = new Location(null, 1, 2, 3);

    private final Runnable task = () -> {};

    @Test
    void theGlobalRegionLandsOnTheSyncScheduler() {
        Region global = scheduler.global();
        global.run(task);
        global.runLater(task, 3);
        global.runRepeating(task, 2, 4);
        assertCalls("runTaskLater", "runTaskLater", "runTaskTimer");
        assertArrayEquals(new Object[] {0L}, bukkit.calls.get(0).args());
        assertArrayEquals(new Object[] {3L}, bukkit.calls.get(1).args());
        assertArrayEquals(new Object[] {2L, 4L}, bukkit.calls.get(2).args());
    }

    @Test
    void locationsAndEntitiesShareTheMainThreadRegion() {
        assertSame(scheduler.global(), scheduler.at(location));
        assertSame(scheduler.global(), scheduler.on(null));
    }

    @Test
    void asyncWorkLandsOnTheAsyncSchedulerInTicks() {
        Async async = scheduler.async();
        async.run(task);
        async.runLater(task, Duration.ofMillis(60));
        async.runRepeating(task, Duration.ZERO, Duration.ofMillis(10));
        assertCalls("runTaskLaterAsynchronously", "runTaskLaterAsynchronously", "runTaskTimerAsynchronously");
        assertArrayEquals(new Object[] {0L}, bukkit.calls.get(0).args());
        assertArrayEquals(new Object[] {2L}, bukkit.calls.get(1).args());
        assertArrayEquals(new Object[] {0L, 1L}, bukkit.calls.get(2).args());
    }

    @Test
    void aZeroPeriodRepeatsEveryTick() {
        scheduler.global().runRepeating(task, 0, 0);
        assertArrayEquals(new Object[] {0L, 1L}, bukkit.calls.get(0).args());
    }

    @Test
    void cancelAllCancelsTheHandlesAndNothingElse() {
        Task once = scheduler.global().run(task);
        Task repeating = scheduler.global().runRepeating(task, 1, 1);
        scheduler.cancelAll();
        assertTrue(once.isCancelled());
        assertTrue(repeating.isCancelled());
        assertEquals(1, bukkit.cancels.get(0)[0]);
        assertEquals(1, bukkit.cancels.get(1)[0]);
        assertEquals(0, bukkit.cancelTasksCalls);
    }

    @Test
    void aFinishedOneShotTaskLeavesTheSet() {
        Task once = scheduler.global().run(task);
        bukkit.bodies.get(0).run();
        scheduler.cancelAll();
        assertFalse(once.isCancelled());
        assertEquals(0, bukkit.cancels.get(0)[0]);
    }

    @Test
    void cancellingAFinishedOneShotTaskLeavesItUncancelled() {
        Task once = scheduler.global().run(task);
        bukkit.bodies.get(0).run();
        once.cancel();
        assertFalse(once.isCancelled());
        assertEquals(0, bukkit.cancels.get(0)[0]);
    }

    @Test
    void aRepeatingTaskStaysUntilCancelled() {
        scheduler.global().runRepeating(task, 1, 1);
        bukkit.bodies.get(0).run();
        scheduler.cancelAll();
        assertEquals(1, bukkit.cancels.get(0)[0]);
    }

    @Test
    void cancellingAHandleCancelsItsTaskOnce() {
        Task handle = scheduler.global().run(task);
        handle.cancel();
        assertTrue(handle.isCancelled());
        scheduler.cancelAll();
        assertEquals(1, bukkit.cancels.get(0)[0]);
    }

    @Test
    void aTaskCancelledByTheSchedulerReportsCancelled() {
        Task handle = scheduler.global().runRepeating(task, 1, 1);
        assertFalse(handle.isCancelled());
        bukkit.cancels.get(0)[0]++;
        assertTrue(handle.isCancelled());
    }

    @Test
    void aTaskCancelledWhileBeingScheduledIsCancelledOnceScheduled() {
        bukkit.duringSchedule = scheduler::cancelAll;
        Task handle = scheduler.global().runRepeating(task, 1, 1);
        assertTrue(handle.isCancelled());
        assertEquals(1, bukkit.cancels.get(0)[0]);
    }

    @Test
    void aRunningRepeatingTaskSurvivesASweep() {
        Task running = scheduler.global().runRepeating(task, 1, 1);
        bukkit.runningTask = 0;
        assertFalse(running.isCancelled());
        for (int i = 0; i < TaskTracker.SWEEP_FLOOR; i++) scheduler.global().runRepeating(task, 1, 1);
        bukkit.runningTask = -1;
        scheduler.cancelAll();
        assertEquals(1, bukkit.cancels.get(0)[0]);
    }

    @Test
    void tasksCancelledByThePlatformAreSweptFromTheSet() {
        for (int i = 0; i < TaskTracker.SWEEP_FLOOR; i++) scheduler.global().runRepeating(task, 1, 1);
        for (int[] cancelled : bukkit.cancels) cancelled[0]++;
        scheduler.global().runRepeating(task, 1, 1);
        scheduler.cancelAll();
        for (int i = 0; i < TaskTracker.SWEEP_FLOOR; i++)
            assertEquals(1, bukkit.cancels.get(i)[0]);
        assertEquals(1, bukkit.cancels.get(TaskTracker.SWEEP_FLOOR)[0]);
    }

    @Test
    void aRejectedTaskPropagatesAndIsNotTracked() {
        bukkit.failure = new IllegalStateException("Plugin attempted to register task while disabled");
        assertThrows(IllegalStateException.class, () -> scheduler.global().run(task));
        scheduler.cancelAll();
        assertTrue(bukkit.cancels.isEmpty());
    }

    @Test
    void everyRegionAsksThePrimaryThread() {
        assertTrue(scheduler.global().isCurrentThread());
        assertTrue(scheduler.at(location).isCurrentThread());
        assertTrue(scheduler.on(null).isCurrentThread());
        BukkitScheduling offThread = new BukkitScheduling(SchedulerStubs.plugin(false), bukkit::scheduler);
        assertFalse(offThread.global().isCurrentThread());
        assertFalse(offThread.on(null).isCurrentThread());
    }

    @Test
    void durationsRoundUpToWholeTicks() {
        assertEquals(0, BukkitScheduling.ticks(Duration.ZERO));
        assertEquals(1, BukkitScheduling.ticks(Duration.ofMillis(1)));
        assertEquals(1, BukkitScheduling.ticks(Duration.ofMillis(50)));
        assertEquals(2, BukkitScheduling.ticks(Duration.ofMillis(51)));
        assertEquals(0, BukkitScheduling.ticks(Duration.ofMillis(-5)));
    }

    private void assertCalls(String... methods) {
        assertEquals(methods.length, bukkit.calls.size());
        for (int i = 0; i < methods.length; i++)
            assertEquals(methods[i], bukkit.calls.get(i).method());
    }
}
