package org.weftkit.wiring.runtime;

import org.weftkit.wiring.registry.Dependency;

/**
 * Resolves one constructor dependency to the value injected for it. The role {@link Components}
 * depends on, so the core invokes resolution as a strategy without naming the concrete
 * {@link Resolution}.
 */
@FunctionalInterface
interface DependencyResolver {
    Object resolve(Dependency dependency, Object[] arguments, Class<?> component);
}
