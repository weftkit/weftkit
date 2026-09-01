package org.weftkit.wiring;

/**
 * Activation hooks a {@link WeftModule} runs against the fully loaded graph, unlike {@link Loader}
 * hooks, which fire at the module's own load position. Modules activate in load order and
 * deactivate in reverse, on the same instances.
 */
public interface ModuleLifecycle {

    /** Wires this module's components into the platform. Runs after load. False aborts startup. */
    default boolean activate() {
        return true;
    }

    /** Undoes {@link #activate()}. Runs before the graph tears down. */
    default void deactivate() {}
}
