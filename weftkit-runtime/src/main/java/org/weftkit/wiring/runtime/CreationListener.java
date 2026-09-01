package org.weftkit.wiring.runtime;

/**
 * A hook into the component core's creation lifecycle. Features observe construction and teardown
 * through these callbacks. Products capture on load, module holders initialize before build, so
 * the core stays closed to them. Every method defaults to a no-op.
 */
interface CreationListener {

    // Before a component's dependencies are resolved and its factory runs
    default void onBuilding(Class<?> type) {}

    // After a singleton's load hook succeeds and it is recorded for unload
    default void onLoaded(Class<?> type, Object instance) {}

    // When a recorded singleton is rolled back after a later failure. Pairs with a completed
    // onLoaded, so a listener that throws mid-onLoaded must leave no partial state behind
    default void onDropped(Class<?> type) {}

    // The graph unloaded, so discard any accumulated state
    default void reset() {}
}
