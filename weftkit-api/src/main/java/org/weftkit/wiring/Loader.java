package org.weftkit.wiring;

/**
 * Lifecycle hooks a {@link Singleton} runs as it is created and torn down. Returning false from
 * {@link #load()} aborts startup, or fails just the triggering injection when the singleton
 * materializes lazily. {@link #unload()} runs in reverse creation order on shutdown.
 */
@FunctionalInterface
public interface Loader {

    boolean load();

    default void unload() {}
}
