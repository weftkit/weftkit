package org.weftkit.wiring.runtime;

/**
 * A strategy for obtaining an instance of a wired component. The eager and prototype scopes are
 * built into {@link Components}. Lazy singletons layer on as their own {@link LazyScope}, and a
 * further scope can be added without touching the component core.
 */
@FunctionalInterface
interface Scope {
    Object obtain(Class<?> type, Object[] arguments);
}
