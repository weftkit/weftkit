package org.weftkit.wiring.bukkit.scheduler;

import static org.junit.jupiter.api.Assertions.*;
import static org.weftkit.wiring.registry.TestRegistry.on;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;
import org.weftkit.wiring.Loader;
import org.weftkit.wiring.registry.TestRegistry;
import org.weftkit.wiring.runtime.WeftLoader;

class TaskScopeTest {
    private final SchedulerStubs.RecordingScheduler bukkit = new SchedulerStubs.RecordingScheduler();
    private final Scheduler scheduler = new BukkitScheduling(SchedulerStubs.plugin(true), bukkit::scheduler);

    private TestRegistry registry() {
        return new TestRegistry()
                .eager(SchedulerModule.class, args -> new SchedulerModule(scheduler))
                .eager(SchedulerModule.TaskScopes.class, args -> new SchedulerModule.TaskScopes(scheduler))
                .ownedProduct(
                        TaskScope.class,
                        SchedulerModule.TaskScopes.class,
                        owner -> ((SchedulerModule.TaskScopes) owner).taskScope());
    }

    @Test
    void closingAScopeDoesNotCancelSiblingOrParentTasks() {
        TaskScope first = scheduler.scope();
        TaskScope second = scheduler.scope();
        Task owned = first.global().run(() -> {});
        Task sibling = second.async().run(() -> {});
        Task parent = scheduler.global().run(() -> {});
        first.close();
        assertTrue(owned.isCancelled());
        assertFalse(sibling.isCancelled());
        assertFalse(parent.isCancelled());
        assertThrows(IllegalStateException.class, () -> first.global().run(() -> {}));
        assertNotNull(second.global().run(() -> {}));
        assertNotNull(scheduler.global().run(() -> {}));
        second.close();
        scheduler.close();
    }

    @Test
    void everyRouteSharesScopeOwnershipAndRetainedHandlesRejectWorkAfterClose() {
        TaskScope scope = scheduler.scope();
        Region region = scope.at(new Location(null, 1, 2, 3));
        Region entity = scope.on(null);
        Async async = scope.async();
        Task[] tasks = {
            scope.global().run(() -> {}),
            region.runLater(() -> {}, 5),
            entity.runRepeating(() -> {}, 2, 3),
            async.runLater(() -> {}, Duration.ofSeconds(1)),
            async.runRepeating(() -> {}, Duration.ZERO, Duration.ofSeconds(1))
        };
        assertTrue(region.isCurrentThread());
        scope.close();
        for (Task task : tasks) assertTrue(task.isCancelled());
        assertThrows(IllegalStateException.class, () -> region.run(() -> {}));
        assertThrows(IllegalStateException.class, () -> entity.run(() -> {}));
        assertThrows(IllegalStateException.class, () -> async.run(() -> {}));
    }

    @Test
    void closeDuringSchedulingCancelsTheLateHandleAndSkipsTheCallback() {
        TaskScope scope = scheduler.scope();
        AtomicInteger runs = new AtomicInteger();
        bukkit.duringSchedule = scope::close;
        Task task = scope.global().run(runs::incrementAndGet);
        assertTrue(task.isCancelled());
        bukkit.bodies.get(0).run();
        assertEquals(0, runs.get());
    }

    @Test
    void completedTasksRemainCompleteAndCancelAllAllowsNewTasks() {
        TaskScope scope = scheduler.scope();
        Task completed = scope.global().run(() -> {});
        bukkit.bodies.get(0).run();
        scope.cancelAll();
        assertFalse(completed.isCancelled());
        assertNotNull(scope.global().run(() -> {}));
        scope.close();
        assertFalse(completed.isCancelled());
    }

    @Test
    void failedConstructionCancelsInjectedOwnedTasks() {
        Task[] task = new Task[1];
        WeftLoader loader = new WeftLoader(registry()
                .eager(
                        String.class,
                        args -> {
                            TaskScope tasks = (TaskScope) args[0];
                            task[0] = tasks.global().runRepeating(() -> {}, 1, 1);
                            throw new IllegalStateException("construction failed");
                        },
                        on(TaskScope.class)));
        assertThrows(RuntimeException.class, loader::load);
        assertTrue(task[0].isCancelled());
    }

    class Owner implements Loader {
        private final Task task;

        Owner(TaskScope tasks) {
            task = tasks.global().runRepeating(() -> {}, 1, 1);
        }

        @Override
        public boolean load() {
            return true;
        }

        @Override
        public void unload() {
            assertTrue(task.isCancelled());
        }
    }

    @Test
    void ownerTasksAreCancelledBeforeItsUnloadHook() {
        WeftLoader loader = new WeftLoader(
                registry().eager(Owner.class, args -> new Owner((TaskScope) args[0]), on(TaskScope.class)));
        assertTrue(loader.load());
        loader.unload();
        assertThrows(IllegalStateException.class, () -> scheduler.global().run(() -> {}));
    }
}
