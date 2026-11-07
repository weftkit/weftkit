package org.weftkit.wiring.bukkit.scheduler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The live tasks of a {@link Scheduler} implementation, {@code T} the platform's task handle with
 * its cancel action and cancelled query. A scheduling call receives the body and a retired action,
 * after which the task reports cancelled.
 *
 * <p>Scheduling and callbacks may run on different threads, and a callback may run before the
 * platform returns its handle. A handle is tracked before scheduling starts. It leaves the set
 * when a one-shot finishes, it is cancelled or retired, or a sweep observes platform cancellation.
 * Completed one-shots stay completed when cancelled later; repeating tasks stay tracked between runs.
 *
 * <p>The {@code running} monitor protects set membership and the sweep threshold. Handle fields
 * are volatile because callbacks and cancellation also access them outside that monitor. Platform
 * scheduling and cancellation run outside the monitor; the cancellation query used by a sweep
 * runs inside it and must be safe to call from any thread.
 */
public final class TaskTracker<T> {

    static final int SWEEP_FLOOR = 16;

    private final Consumer<T> cancel;

    private final Predicate<T> cancelled;

    // Both fields below are guarded by running.
    private final Set<Handle> running = new HashSet<>();

    private int sweepAt = SWEEP_FLOOR;

    private volatile boolean closed;

    public TaskTracker(Consumer<T> cancel, Predicate<T> cancelled) {
        this.cancel = cancel;
        this.cancelled = cancelled;
    }

    /** Schedules a one-shot task, forgotten once it ran. A null platform handle reports cancelled. */
    public Task once(Runnable task, BiFunction<Runnable, Runnable, T> schedule) {
        Handle handle = new Handle();
        return track(handle, schedule, () -> {
            try {
                if (!closed && !handle.cancelled) task.run();
            } finally {
                handle.complete();
            }
        });
    }

    /** Schedules a repeating task, tracked until cancelled. */
    public Task repeating(Runnable task, BiFunction<Runnable, Runnable, T> schedule) {
        Handle handle = new Handle();
        return track(handle, schedule, () -> {
            if (!closed && !handle.cancelled) task.run();
        });
    }

    /** Attempts every cancellation, rethrowing the first failure with the rest suppressed. */
    public void cancelAll() {
        List<Handle> handles;
        synchronized (running) {
            handles = new ArrayList<>(running);
            running.clear();
        }
        Throwable failure = null;
        for (Handle handle : handles) {
            try {
                handle.cancelDelegate();
            } catch (RuntimeException | Error ex) {
                if (failure == null) failure = ex;
                else if (failure != ex) failure.addSuppressed(ex);
            }
        }
        if (failure instanceof Error error) throw error;
        if (failure != null) throw (RuntimeException) failure;
    }

    /**
     * Permanently rejects new scheduling and cancels the tracked tasks. Does not wait for running
     * callbacks or platform scheduling calls; a late platform handle is cancelled when it arrives.
     * Callbacks that have not started are skipped after close, even if platform cancellation fails.
     */
    public void close() {
        synchronized (running) {
            closed = true;
        }
        cancelAll();
    }

    private Task track(Handle handle, BiFunction<Runnable, Runnable, T> schedule, Runnable body) {
        synchronized (running) {
            if (closed) throw new IllegalStateException("Scheduler is closed");
            if (running.size() >= sweepAt) sweep();
            running.add(handle);
        }
        T scheduled;
        try {
            scheduled = schedule.apply(body, handle::retire);
        } catch (RuntimeException | Error ex) {
            forget(handle);
            throw ex;
        }
        if (scheduled == null) {
            forget(handle);
            handle.cancelled = true;
            return handle;
        }
        // Cancellation may have happened before the platform returned its handle. Publishing the
        // delegate before checking the flag ensures either this path or cancelDelegate cancels it.
        handle.delegate = scheduled;
        if (handle.cancelled) cancel.accept(scheduled);
        return handle;
    }

    private void forget(Handle handle) {
        synchronized (running) {
            running.remove(handle);
        }
    }

    // Caller holds the running monitor.
    private void sweep() {
        running.removeIf(Handle::isCancelled);
        sweepAt = Math.max(SWEEP_FLOOR, running.size() * 2);
    }

    private final class Handle implements Task {

        // Null until scheduling returns; cancellation must also work during that interval.
        private volatile T delegate;

        // Explicit cancellation or entity retirement. Platform cancellation is queried separately.
        private volatile boolean cancelled;

        // Only one-shot bodies set this, after they finish (including exceptional completion).
        private volatile boolean completed;

        @Override
        public void cancel() {
            forget(this);
            cancelDelegate();
        }

        @Override
        public boolean isCancelled() {
            if (cancelled) return true;
            if (completed) return false;
            T task = delegate;
            return task != null && TaskTracker.this.cancelled.test(task);
        }

        void cancelDelegate() {
            if (completed) return;
            cancelled = true;
            T task = delegate;
            if (task != null) cancel.accept(task);
        }

        void complete() {
            completed = true;
            forget(this);
        }

        void retire() {
            forget(this);
            cancelled = true;
        }
    }
}
