package org.weftkit.wiring.bukkit.scheduler;

import java.time.Duration;

/** Work off the server threads, see {@link Scheduler#async}. Durations round up to whole ticks on Bukkit. */
public interface Async {

    /** Runs the task as soon as possible. */
    default Task run(Runnable task) {
        return runLater(task, Duration.ZERO);
    }

    /** Runs the task after the delay. */
    Task runLater(Runnable task, Duration delay);

    /** Runs the task every period after the delay. */
    Task runRepeating(Runnable task, Duration delay, Duration period);
}
