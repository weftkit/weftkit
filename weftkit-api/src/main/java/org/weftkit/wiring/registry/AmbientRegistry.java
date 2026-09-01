package org.weftkit.wiring.registry;

import java.util.List;

/**
 * The declared ambient types: the registry class, its constructor parameters,
 * {@code @Registry(ambient)}, and adapter contributions. Every ambient value handed to the
 * loader must match one. Defaults to empty for hand-built registries, accepting no values beyond
 * a {@code FeatureFlags} override.
 */
public interface AmbientRegistry {

    default List<Class<?>> ambientTypes() {
        return List.of();
    }
}
