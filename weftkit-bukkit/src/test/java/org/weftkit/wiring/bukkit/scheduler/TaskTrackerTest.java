package org.weftkit.wiring.bukkit.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TaskTrackerTest {

    @Test
    void closeRejectsNewWorkAndSkipsQueuedCallbacks() {
        AtomicInteger calls = new AtomicInteger();
        List<Runnable> queued = new ArrayList<>();
        TaskTracker<Integer> tracker = new TaskTracker<>(task -> {}, task -> false);
        tracker.once(calls::incrementAndGet, (body, retired) -> {
            queued.add(body);
            return 1;
        });
        tracker.repeating(calls::incrementAndGet, (body, retired) -> {
            queued.add(body);
            return 2;
        });
        tracker.close();
        queued.forEach(Runnable::run);
        assertEquals(0, calls.get());
        assertThrows(
                IllegalStateException.class,
                () -> tracker.once(() -> {}, (body, retired) -> {
                    calls.incrementAndGet();
                    return 3;
                }));
        assertThrows(IllegalStateException.class, () -> tracker.repeating(() -> {}, (body, retired) -> 4));
        assertEquals(0, calls.get());
        tracker.close();
    }

    @Test
    void closeCancelsAHandleReturnedByAnInFlightSubmission() throws InterruptedException {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<Integer> cancelled = new ArrayList<>();
        AtomicReference<Task> task = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        TaskTracker<Integer> tracker = new TaskTracker<>(cancelled::add, value -> false);
        Thread worker = new Thread(() -> {
            try {
                task.set(tracker.once(() -> {}, (body, retired) -> {
                    entered.countDown();
                    await(release);
                    return 1;
                }));
            } catch (Throwable ex) {
                failure.set(ex);
            }
        });
        worker.start();
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            tracker.close();
            assertTrue(cancelled.isEmpty());
        } finally {
            release.countDown();
            worker.join(5000);
        }
        assertTrue(!worker.isAlive());
        assertEquals(null, failure.get());
        assertEquals(List.of(1), cancelled);
        assertTrue(task.get().isCancelled());
    }

    @Test
    void closeDoesNotWaitForAnAlreadyRunningCallback() throws InterruptedException {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Runnable> queued = new AtomicReference<>();
        AtomicInteger finished = new AtomicInteger();
        TaskTracker<Integer> tracker = new TaskTracker<>(task -> {}, task -> false);
        tracker.once(
                () -> {
                    entered.countDown();
                    await(release);
                    finished.incrementAndGet();
                },
                (body, retired) -> {
                    queued.set(body);
                    return 1;
                });
        Thread worker = new Thread(queued.get());
        worker.start();
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            tracker.close();
            assertEquals(0, finished.get());
        } finally {
            release.countDown();
            worker.join(5000);
        }
        assertTrue(!worker.isAlive());
        assertEquals(1, finished.get());
    }

    @Test
    void cancellationRemainsReusableButAFailedCloseStaysClosed() {
        AssertionError original = new AssertionError("cancel");
        TaskTracker<Integer> tracker = new TaskTracker<>(
                task -> {
                    throw original;
                },
                task -> false);
        tracker.cancelAll();
        tracker.once(() -> {}, (body, retired) -> 1);
        assertSame(original, assertThrows(AssertionError.class, tracker::close));
        assertThrows(IllegalStateException.class, () -> tracker.once(() -> {}, (body, retired) -> 2));
        tracker.close();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("Timed out waiting for test release");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError(ex);
        }
    }

    @Test
    void cancellationErrorDoesNotPreventOtherHandlesBeingCancelled() {
        AssertionError original = new AssertionError("cancel");
        List<Integer> attempted = new ArrayList<>();
        TaskTracker<Integer> tracker = new TaskTracker<>(
                task -> {
                    attempted.add(task);
                    throw original;
                },
                task -> false);
        tracker.once(() -> {}, (body, retired) -> 1);
        tracker.once(() -> {}, (body, retired) -> 2);
        assertSame(original, assertThrows(AssertionError.class, tracker::cancelAll));
        assertEquals(2, attempted.size());
        tracker.cancelAll();
    }

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
