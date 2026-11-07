package org.weftkit.wiring.bukkit.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TaskTrackerTest {

    @Test
    void cancellationAttemptsEveryHandleAndAggregatesFailures() {
        Set<Integer> attempted = new HashSet<>();
        TaskTracker<Integer> tracker = new TaskTracker<>(
                task -> {
                    attempted.add(task);
                    throw new IllegalStateException("cancel " + task);
                },
                task -> false);
        List<Task> tasks = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            int id = i;
            tasks.add(tracker.repeating(() -> {}, (body, retired) -> id));
        }
        IllegalStateException failure = assertThrows(IllegalStateException.class, tracker::cancelAll);
        assertEquals(Set.of(0, 1, 2), attempted);
        assertEquals(2, failure.getSuppressed().length);
        assertTrue(tasks.stream().allMatch(Task::isCancelled));
        tracker.cancelAll();
    }

    @Test
    void aSharedPlatformExceptionDoesNotInterruptCancellation() {
        RuntimeException failure = new IllegalStateException("platform unavailable");
        List<Integer> attempted = new ArrayList<>();
        TaskTracker<Integer> tracker = new TaskTracker<>(
                task -> {
                    attempted.add(task);
                    throw failure;
                },
                task -> false);
        tracker.once(() -> {}, (body, retired) -> 1);
        tracker.once(() -> {}, (body, retired) -> 2);
        assertSame(failure, assertThrows(IllegalStateException.class, tracker::cancelAll));
        assertEquals(2, attempted.size());
    }
}
