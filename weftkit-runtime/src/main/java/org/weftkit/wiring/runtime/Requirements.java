package org.weftkit.wiring.runtime;

import java.util.List;
import org.weftkit.wiring.registry.ModuleRegistry;

/**
 * Builds the initializers of a component's required static holders before the component itself,
 * so a {@code @WeftModule}'s holders are populated in time. Observes the component core's build
 * step, so the core stays unaware of the requirement.
 */
final class Requirements implements CreationListener {

    private final ModuleRegistry registry;

    private final Components core;

    Requirements(ModuleRegistry registry, Components core) {
        this.registry = registry;
        this.core = core;
    }

    @Override
    public void onBuilding(Class<?> type) {
        for (Class<?> initializer : registry.requirements().getOrDefault(type, List.of())) core.instance(initializer);
    }
}
