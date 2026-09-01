package org.weftkit.wiring.registry;

import java.util.Set;

/** The components built lazily on first demand rather than eagerly at load. */
public interface LazyRegistry {

    Set<Class<?>> lazySingletons();
}
