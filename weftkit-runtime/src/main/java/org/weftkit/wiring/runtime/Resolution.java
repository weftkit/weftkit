package org.weftkit.wiring.runtime;

import java.util.List;
import org.weftkit.wiring.runtime.exception.ResolutionException;
import org.weftkit.wiring.runtime.registry.Dependency;
import org.weftkit.wiring.runtime.registry.LazyRegistry;

/**
 * Resolves one constructor dependency through ordered layers: explicit arguments, ambient
 * values, the facades, the wired component core, and products. Each layer gates itself, and an
 * unresolved optional dependency settles as absent.
 */
final class Resolution implements DependencyResolver {

    private static final Object UNRESOLVED = new Object();

    private final LazyRegistry registry;

    private final AmbientValues ambient;

    private final Weft weft;

    private final WeftView view;

    private final Instantiator components;

    private final FlagGate gate;

    private final Products products;

    // The extension layers know neither the component core nor each other
    private final List<Layer> layers =
            List.of(this::fromArgument, this::fromAmbient, this::fromFacade, this::fromComponent, this::fromProduct);

    Resolution(
            LazyRegistry registry,
            AmbientValues ambient,
            Weft weft,
            WeftView view,
            Instantiator components,
            FlagGate gate,
            Products products) {
        this.registry = registry;
        this.ambient = ambient;
        this.weft = weft;
        this.view = view;
        this.components = components;
        this.gate = gate;
        this.products = products;
    }

    @Override
    public Object resolve(Dependency dependency, Object[] arguments, Class<?> component) {
        for (Layer layer : layers) {
            Object outcome = layer.resolve(dependency, arguments, component);
            if (outcome != UNRESOLVED) return outcome;
        }
        if (dependency.optional()) return null;
        String message = "Cannot resolve dependency " + dependency.type().getName() + " for " + component.getName();
        if (dependency.qualifier().isEmpty() && ambient.declared(dependency.type()))
            message += ". A matching ambient type is declared but its value is absent";
        throw new ResolutionException(message);
    }

    // Arguments carry no qualifier, so only an unqualified dependency may match one. Among
    // several assignable arguments the first given wins
    private Object fromArgument(Dependency dependency, Object[] arguments, Class<?> component) {
        if (!dependency.qualifier().isEmpty()) return UNRESOLVED;
        for (Object candidate : arguments) if (dependency.type().isInstance(candidate)) return candidate;
        return UNRESOLVED;
    }

    // Ambient roots carry no qualifier either
    private Object fromAmbient(Dependency dependency, Object[] arguments, Class<?> component) {
        if (!dependency.qualifier().isEmpty()) return UNRESOLVED;
        Object match = ambient.match(dependency.type());
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
        Class<?> owner = products.owner(dependency.type(), dependency.qualifier());
        if (owner == null) return UNRESOLVED;
        Object cached = products.captured(dependency.type(), dependency.qualifier());
        if (cached != null) return cached;
        // The owner may still be inside its own load hook, so fall back to the live getter
        Object provider = components.created(owner);
        // Deliberate for optional product dependencies too, they materialize instead of staying
        // empty, unless the owner is gated off by a feature flag
        if (provider == null && registry.lazySingletons().contains(owner) && gate.enabled(owner))
            provider = components.instance(owner);
        Object product =
                provider == null ? null : products.extract(dependency.type(), dependency.qualifier(), provider);
        if (product == null && !dependency.optional())
            throw new ResolutionException(
                    "Dependency is not available yet: " + dependency.type().getName());
        return product;
    }

    // One resolution layer: a value, null for a dependency resolved as absent, or UNRESOLVED
    @FunctionalInterface
    private interface Layer {
        Object resolve(Dependency dependency, Object[] arguments, Class<?> component);
    }
}
