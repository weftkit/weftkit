package org.weftkit.wiring;

/**
 * Lifecycle hooks a {@link Singleton} runs as it is created and torn down. Returning false from
 * {@link #load()} aborts startup, or fails just the triggering injection when the singleton
 * materializes lazily. A constructed singleton whose {@code load()} throws or returns false
 * receives {@link #unload()} immediately, before its dependencies are torn down. On successful
 * load, {@code unload()} runs in reverse creation order on shutdown. Cleanup must tolerate a
 * partially completed load; constructors that throw must release their own resources.
 */
@FunctionalInterface
public interface Loader {

    boolean load();

    default void unload() {}
}
