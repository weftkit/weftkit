package org.weftkit.wiring.bukkit.scheduler;

/** A handle on a task scheduled through a {@link Scheduler}. */
public interface Task {

    /** Cancels the task. A task that already ran or was cancelled is unaffected. */
    void cancel();

    /** Returns whether the task was cancelled, through this handle or by the scheduler. */
    boolean isCancelled();
}
