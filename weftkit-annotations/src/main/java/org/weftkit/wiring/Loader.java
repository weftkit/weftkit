package org.weftkit.wiring;

/**
 * Lifecycle hooks a {@link Singleton} runs as it is created and torn down. Returning false from
 * {@link #load()} aborts startup; {@link #unload()} runs in reverse load order on shutdown.
 */
@FunctionalInterface
public interface Loader {

    boolean load();

    default void unload() {}
}
