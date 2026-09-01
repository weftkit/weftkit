package org.weftkit.wiring.runtime.registry;

import java.util.List;
import java.util.Map;

/**
 * The {@code @WeftModule} data: the module components in load order, and the initializer
 * components of each component's required static holders, built before the component itself. Both
 * default to empty for hand-built registries.
 */
public interface ModuleRegistry {

    default List<Class<?>> modules() {
        return List.of();
    }

    default Map<Class<?>, List<Class<?>>> requirements() {
        return Map.of();
    }
}
