package org.weftkit.wiring.bukkit.scheduler;

/** A place server-side work runs, the main thread on Bukkit and Paper. A delay of {@code 0} is the next tick. */
public interface Region {

    /** Runs the task on the next tick. */
    default Task run(Runnable task) {
        return runLater(task, 0);
    }

    /** Runs the task after the delay in ticks. */
    Task runLater(Runnable task, long delayTicks);

    /** Runs the task every period after the delay, both in ticks. */
    Task runRepeating(Runnable task, long delayTicks, long periodTicks);

    /** Whether the current thread ticks this region. */
    boolean isCurrentThread();
}
