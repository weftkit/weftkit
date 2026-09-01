package org.weftkit.wiring.runtime;

import java.util.HashMap;
import java.util.Map;
import org.weftkit.wiring.runtime.exception.ComponentLoadException;

/**
 * The lazy singleton scope: builds its component on first demand, caches the instance, and keeps
 * a failure sticky so a broken singleton is not retried until {@link #resetFailure} or unload.
 * Rides the shared managed-creation kernel, so the core stays unaware of lazy singletons, and
 * observes creation only to clear failures on unload.
 */
final class LazyScope implements Scope, CreationListener {

    private final Components core;

    // First failure per lazy singleton, blocks further attempts until resetFailure or unload
    private final Map<Class<?>, RuntimeException> failures = new HashMap<>();

    LazyScope(Components core) {
        this.core = core;
    }

    // The cached instance must not depend on one call site, so lazy singletons ignore arguments
    @Override
    public Object obtain(Class<?> type, Object[] arguments) {
        Object existing = core.created(type);
        if (existing != null) return existing;
        RuntimeException earlier = failures.get(type);
        if (earlier != null)
            throw new ComponentLoadException(type.getName() + " (failed earlier, stays failed until reset)", earlier);
        try {
            // A declined load hook is an error for a lazy singleton, unlike the eager graph
            if (!core.loadSingleton(type)) throw new ComponentLoadException(type.getName());
        } catch (RuntimeException ex) {
            failures.put(type, ex);
            throw ex;
        }
        return core.created(type);
    }

    boolean resetFailure(Class<?> type) {
        return failures.remove(type) != null;
    }

    @Override
    public void reset() {
        failures.clear();
    }
}
