package org.weftkit.wiring.runtime;

import java.util.List;
import java.util.Map;
import org.weftkit.wiring.runtime.registry.ComponentRegistry;
import org.weftkit.wiring.runtime.registry.Dependency;

/**
 * Resolves one constructor dependency through ordered layers: explicit arguments, ambient
 * values, the facades, the wired component core, and products. Each layer gates itself, and an
 * unresolved optional dependency settles as absent.
 */
final class Resolution {

    private static final Object UNRESOLVED = new Object();

    private final ComponentRegistry registry;

    private final Object[] ambient;

    private final Weft weft;

    private final WeftView view;

    private final Components components;

    private final FlagGate gate;

    // The extension layers know neither the component core nor each other
    private final List<Layer> layers =
            List.of(this::fromArgument, this::fromAmbient, this::fromFacade, this::fromComponent, this::fromProduct);

    Resolution(
            ComponentRegistry registry,
            Object[] ambient,
            Weft weft,
            WeftView view,
            Components components,
            FlagGate gate) {
        this.registry = registry;
        this.ambient = ambient;
        this.weft = weft;
        this.view = view;
        this.components = components;
        this.gate = gate;
    }

    Object resolve(Dependency dependency, Object[] arguments, Class<?> component) {
        for (Layer layer : layers) {
            Object outcome = layer.resolve(dependency, arguments, component);
            if (outcome != UNRESOLVED) return outcome;
        }
        if (dependency.optional()) return null;
        throw new ResolutionException(
                "Cannot resolve dependency " + dependency.type().getName() + " for " + component.getName());
    }

    // Arguments carry no qualifier, so only an unqualified dependency may match one
    private Object fromArgument(Dependency dependency, Object[] arguments, Class<?> component) {
        if (!dependency.qualifier().isEmpty()) return UNRESOLVED;
        Object match = unique(arguments, dependency.type(), component, "argument");
        return match != null ? match : UNRESOLVED;
    }

    // Ambient roots carry no qualifier either
    private Object fromAmbient(Dependency dependency, Object[] arguments, Class<?> component) {
        if (!dependency.qualifier().isEmpty()) return UNRESOLVED;
        Object match = unique(ambient, dependency.type(), component, "ambient value");
        return match != null ? match : UNRESOLVED;
    }

    private Object fromFacade(Dependency dependency, Object[] arguments, Class<?> component) {
        if (!dependency.qualifier().isEmpty()) return UNRESOLVED;
        if (dependency.type() == Weft.class) return weft;
        if (dependency.type() == WeftView.class) return view;
        return UNRESOLVED;
    }

    // The core layer, everything the graph constructs itself
    private Object fromComponent(Dependency dependency, Object[] arguments, Class<?> component) {
        Class<?> target = components.bound(dependency.type(), dependency.qualifier());
        if (target == null || !components.wired(target)) return UNRESOLVED;
        // A flag-gated-off component resolves as absent. The processor rejects a required
        // dependency on a flagged component, so only an optional one reaches the empty case
        if (!gate.enabled(target)) {
            if (dependency.optional()) return null;
            throw new ResolutionException("Required dependency "
                    + dependency.type().getName() + " of " + component.getName() + " is gated off by its feature flag");
        }
        return components.instance(target);
    }

    private Object fromProduct(Dependency dependency, Object[] arguments, Class<?> component) {
        Map<String, Class<?>> owners = registry.productOwners().get(dependency.type());
        Class<?> owner = owners == null ? null : owners.get(dependency.qualifier());
        if (owner == null) return UNRESOLVED;
        Object cached = components.capturedProduct(dependency.type(), dependency.qualifier());
        if (cached != null) return cached;
        // The owner may still be inside its own load hook, so fall back to the live getter
        Object provider = components.created(owner);
        // Deliberate for optional product dependencies too, they materialize instead of staying
        // empty, unless the owner is gated off by a feature flag
        if (provider == null && registry.lazySingletons().contains(owner) && gate.enabled(owner))
            provider = components.materialize(owner);
        Object product = provider == null
                ? null
                : registry.productGetters()
                        .get(dependency.type())
                        .get(dependency.qualifier())
                        .apply(provider);
        if (product == null && !dependency.optional())
            throw new ResolutionException(
                    "Dependency is not available yet: " + dependency.type().getName());
        return product;
    }

    private Object unique(Object[] candidates, Class<?> type, Class<?> component, String kind) {
        Object match = null;
        for (Object candidate : candidates) {
            if (!type.isInstance(candidate)) continue;
            if (match != null)
                throw new ResolutionException(
                        "Ambiguous " + kind + " for " + type.getName() + " of " + component.getName());
            match = candidate;
        }
        return match;
    }

    // One resolution layer: a value, null for a dependency resolved as absent, or UNRESOLVED
    @FunctionalInterface
    private interface Layer {
        Object resolve(Dependency dependency, Object[] arguments, Class<?> component);
    }
}
