package org.weftkit.wiring.bukkit.scheduler;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

/**
 * Schedules work for the plugin after Folia's threaded regions. Injectable everywhere, every
 * task cancelled as the graph unloads.
 */
public interface Scheduler {

    /** The global region: the main thread on Bukkit and Paper, the global tick thread on Folia. */
    Region global();

    /** The region owning the location, the main thread on Bukkit and Paper. */
    Region at(Location location);

    /** The entity's own scheduler, the main thread on Bukkit and Paper. */
    Region on(Entity entity);

    /** Work off the server threads. */
    Async async();

    /** Cancels every task scheduled through this scheduler. For the adapter: components cancel through {@link Task}. */
    void cancelAll();
}
