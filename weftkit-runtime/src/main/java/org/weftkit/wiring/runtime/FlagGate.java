package org.weftkit.wiring.runtime;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import org.weftkit.wiring.FeatureFlags;
import org.weftkit.wiring.registry.Flag;
import org.weftkit.wiring.registry.FlagRegistry;
import org.weftkit.wiring.runtime.exception.ResolutionException;

/**
 * Decides per component whether its feature flag lets it be constructed. Resolves the
 * {@link FeatureFlags} provider once, under the graph lock, ambient values winning over a wired
 * implementation, and caches the instance, not its answers, so checks stay live.
 */
final class FlagGate {

    private final FlagRegistry registry;

    private final Supplier<Set<Class<?>>> wiredTypes;

    private final Supplier<FeatureFlags> ambient;

    private final Function<Class<?>, Object> instances;

    private final Function<Supplier<FeatureFlags>, FeatureFlags> locked;

    // Never unwrap, a resolved null must stay distinct from unresolved
    private record Resolved(FeatureFlags flags) {}

    private volatile Resolved resolved;

    FlagGate(
            FlagRegistry registry,
            Supplier<Set<Class<?>>> wiredTypes,
            Supplier<FeatureFlags> ambient,
            Function<Class<?>, Object> instances,
            Function<Supplier<FeatureFlags>, FeatureFlags> locked) {
        this.registry = registry;
        this.wiredTypes = wiredTypes;
        this.ambient = ambient;
        this.instances = instances;
        this.locked = locked;
    }

    // Flags come from the registry, no annotation lookup at runtime
    boolean enabled(Class<?> type) {
        Flag flag = registry.featureFlags().get(type);
        return flag == null || FeatureGate.enabled(flag, flags());
    }

    // The provider may have come from the graph that unload just tore down
    void reset() {
        resolved = null;
    }

    private FeatureFlags flags() {
        Resolved current = resolved;
        return current != null ? current.flags() : locked.apply(this::resolve);
    }

    // The processor orders a FeatureFlags component before every flagged component, so
    // resolution here never hits an unloaded eager singleton
    private FeatureFlags resolve() {
        Resolved current = resolved;
        if (current != null) return current.flags();
        FeatureFlags provided = ambient.get();
        FeatureFlags flags = provided != null ? provided : graphFlags();
        // Only on success, so a failed resolution rethrows instead of defaulting
        resolved = new Resolved(flags);
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
