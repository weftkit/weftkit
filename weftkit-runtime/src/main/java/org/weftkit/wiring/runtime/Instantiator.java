package org.weftkit.wiring.runtime;

/**
 * The component core as {@link Resolution} sees it: apply interface bindings, test whether a type
 * is wired, and obtain instances. The role resolution depends on, so it resolves against the core
 * without naming the concrete {@link Components}.
 */
interface Instantiator {

    /** The bound implementation for the type and qualifier, or null when none is bound. */
    Class<?> bound(Class<?> type, String qualifier);

    /** Whether the type itself is wired, bindings not applied. */
    boolean wired(Class<?> type);

    /** Creates or fetches an instance of the type, applying its scope. */
    Object instance(Class<?> type, Object... arguments);

    /** The raw cached singleton for the type without binding resolution, or null. */
    Object created(Class<?> type);
}
