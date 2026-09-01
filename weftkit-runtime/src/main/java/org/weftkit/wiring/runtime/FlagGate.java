package org.weftkit.wiring.runtime;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import org.weftkit.wiring.runtime.exception.ResolutionException;
import org.weftkit.wiring.runtime.registry.Flag;
import org.weftkit.wiring.runtime.registry.FlagRegistry;

/**
 * Decides per component whether its feature flag lets it be constructed. Resolves the
 * {@link FeatureFlags} provider once, ambient values winning over a wired implementation, and
 * caches the instance, not its answers, so checks stay live.
 */
final class FlagGate {

    private final FlagRegistry registry;

    private final Supplier<Set<Class<?>>> wiredTypes;

    private final Supplier<FeatureFlags> ambient;

    private final Function<Class<?>, Object> instances;

    private FeatureFlags flags;

    private boolean resolved;

    FlagGate(
            FlagRegistry registry,
            Supplier<Set<Class<?>>> wiredTypes,
            Supplier<FeatureFlags> ambient,
            Function<Class<?>, Object> instances) {
        this.registry = registry;
        this.wiredTypes = wiredTypes;
        this.ambient = ambient;
        this.instances = instances;
    }

    // Flags come from the registry, no annotation lookup at runtime
    boolean enabled(Class<?> type) {
        Flag flag = registry.featureFlags().get(type);
        return flag == null || FeatureGate.enabled(flag, flags());
    }

    // The provider may have come from the graph that unload just tore down
    void reset() {
        flags = null;
        resolved = false;
    }

    // The processor orders a FeatureFlags component before every flagged component, so
    // resolution here never hits an unloaded eager singleton
    private FeatureFlags flags() {
        if (resolved) return flags;
        FeatureFlags provided = ambient.get();
        flags = provided != null ? provided : graphFlags();
        // Only on success, so a failed resolution rethrows instead of defaulting
        resolved = true;
        return flags;
    }

    private FeatureFlags graphFlags() {
        List<Class<?>> implementations = wiredTypes.get().stream()
                .filter(FeatureFlags.class::isAssignableFrom)
                .toList();
        if (implementations.isEmpty()) return null;
        if (implementations.size() > 1)
            throw new ResolutionException("Ambiguous FeatureFlags implementations: "
                    + implementations.stream().map(Class::getName).toList());
        return (FeatureFlags) instances.apply(implementations.get(0));
    }
}
