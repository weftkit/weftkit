package org.weftkit.wiring.runtime;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.weftkit.wiring.Loader;
import org.weftkit.wiring.runtime.registry.ComponentRegistry;
import org.weftkit.wiring.runtime.registry.Dependency;

/**
 * The wired component core: builds components, caches singletons, runs {@link Loader} hooks,
 * and keeps the bookkeeping that unload and diagnostics read. Dependency resolution and flag
 * gating are layered on top and attach through {@link #connect}.
 */
final class Components {

    private final ComponentRegistry registry;

    private final Set<Class<?>> eager;

    private final Map<Class<?>, Object> singletons = new HashMap<>();

    // First failure per lazy singleton, blocks further attempts until resetFailure or unload
    private final Map<Class<?>, RuntimeException> failures = new HashMap<>();

    // Completion order of singleton creation, eager and lazy interleaved, driving reverse-order
    // unload. Insertion order of the singletons map would not do: a hook-materialized lazy
    // singleton is inserted after its consumer but completes before it
    private final List<Class<?>> creationOrder = new ArrayList<>();

    private final Map<Class<?>, Duration> timings = new LinkedHashMap<>();

    private final Map<Class<?>, Map<String, Object>> products = new HashMap<>();

    private Resolution resolution;

    private FlagGate gate;

    Components(ComponentRegistry registry) {
        this.registry = registry;
        this.eager = Set.copyOf(registry.loadOrder());
    }

    // Resolution and the gate call back into the core, so they attach after construction
    void connect(Resolution resolution, FlagGate gate) {
        this.resolution = resolution;
        this.gate = gate;
    }

    boolean loadEager() {
        for (Class<?> type : registry.loadOrder()) {
            // A singleton gated off by its feature flag is never constructed
            if (!gate.enabled(type)) continue;
            long start = System.nanoTime();
            boolean hookCompleted = false;
            try {
                Object singleton = build(type);
                // Publish before the load hook runs so the hook can resolve this singleton and its products
                singletons.put(type, singleton);
                if (singleton instanceof Loader loader && !loader.load()) {
                    singletons.remove(type);
                    unloadAll();
                    return false;
                }
                hookCompleted = true;
                creationOrder.add(type);
                captureProducts(type, singleton);
            } catch (RuntimeException ex) {
                // A singleton whose hook never completed is treated like an abort and not unloaded
                if (!hookCompleted) singletons.remove(type);
                try {
                    unloadAll();
                } catch (RuntimeException teardown) {
                    ex.addSuppressed(teardown);
                }
                throw ex;
            }
            timings.put(type, Duration.ofNanos(System.nanoTime() - start));
        }
        return true;
    }

    void unloadAll() {
        List<Class<?>> order = List.copyOf(creationOrder);
        RuntimeException failure = null;
        for (int index = order.size() - 1; index >= 0; index--) {
            if (!(singletons.remove(order.get(index)) instanceof Loader loader)) continue;
            try {
                loader.unload();
            } catch (RuntimeException ex) {
                if (failure == null) failure = ex;
                else failure.addSuppressed(ex);
            }
        }
        singletons.clear();
        creationOrder.clear();
        products.clear();
        failures.clear();
        gate.reset();
        if (failure != null) throw failure;
    }

    boolean resetFailure(Class<?> type) {
        return failures.remove(bound(type, "")) != null;
    }

    /** The cached singleton, interface bindings applied, or null when none has been created. */
    Object cached(Class<?> type) {
        return singletons.get(bound(type, ""));
    }

    /** The raw singleton without binding resolution, or null. */
    Object created(Class<?> type) {
        return singletons.get(type);
    }

    Map<Class<?>, Duration> timingsSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(timings));
    }

    Duration totalTime() {
        return timings.values().stream().reduce(Duration.ZERO, Duration::plus);
    }

    Object instance(Class<?> type, Object... arguments) {
        if (eager.contains(type)) return loaded(type);
        // The cached instance must not depend on one call site, so lazy singletons ignore arguments
        if (registry.lazySingletons().contains(type)) return materialize(type);
        return build(type, arguments);
    }

    Object materialize(Class<?> type) {
        Object singleton = singletons.get(type);
        if (singleton != null) return singleton;
        RuntimeException earlier = failures.get(type);
        if (earlier != null)
            throw new ComponentLoadException(type.getName() + " (failed earlier, stays failed until reset)", earlier);
        long start = System.nanoTime();
        Object created;
        try {
            created = build(type);
        } catch (RuntimeException ex) {
            failures.put(type, ex);
            throw ex;
        }
        // Publish before the load hook runs so the hook can resolve this singleton and its products
        singletons.put(type, created);
        boolean hookCompleted = false;
        try {
            if (created instanceof Loader loader && !loader.load()) throw new ComponentLoadException(type.getName());
            hookCompleted = true;
            creationOrder.add(type);
            captureProducts(type, created);
        } catch (RuntimeException ex) {
            // Only this singleton is dropped, the graph stays up. The marker blocks rebuild loops
            if (hookCompleted) {
                if (created instanceof Loader loader) {
                    try {
                        loader.unload();
                    } catch (RuntimeException teardown) {
                        ex.addSuppressed(teardown);
                    }
                }
                creationOrder.remove(type);
                dropProducts(type);
            }
            singletons.remove(type);
            failures.put(type, ex);
            throw ex;
        }
        timings.put(type, Duration.ofNanos(System.nanoTime() - start));
        return created;
    }

    Object capturedProduct(Class<?> type, String qualifier) {
        Map<String, Object> captured = products.get(type);
        return captured == null ? null : captured.get(qualifier);
    }

    Class<?> bound(Class<?> type, String qualifier) {
        Map<String, Class<?>> bindings = registry.bindings().get(type);
        Class<?> implementation = bindings == null ? null : bindings.get(qualifier);
        if (implementation != null) return implementation;
        return qualifier.isEmpty() ? type : null;
    }

    boolean wired(Class<?> type) {
        return registry.parameters().containsKey(type);
    }

    private Object build(Class<?> target, Object... arguments) {
        // Initializers of required static holders must have filled them before the target runs
        for (Class<?> initializer : registry.requirements().getOrDefault(target, List.of())) instance(initializer);
        Object[] parameters = parameters(target).stream()
                .map(dependency -> resolution.resolve(dependency, arguments, target))
                .toArray();
        try {
            return registry.factories().get(target).apply(parameters);
        } catch (RuntimeException ex) {
            throw new ComponentLoadException(target.getName(), ex);
        }
    }

    private List<Dependency> parameters(Class<?> type) {
        List<Dependency> parameters = registry.parameters().get(type);
        if (parameters == null)
            throw new ResolutionException("Component is not annotated with @Wired: " + type.getName());
        return parameters;
    }

    private Object loaded(Class<?> type) {
        Object singleton = singletons.get(type);
        if (singleton == null) throw new ResolutionException("Singleton dependency is not loaded: " + type.getName());
        return singleton;
    }

    private void captureProducts(Class<?> owner, Object singleton) {
        registry.productOwners()
                .forEach((product, qualified) -> qualified.forEach((qualifier, type) -> {
                    if (!type.equals(owner)) return;
                    Object value = registry.productGetters()
                            .get(product)
                            .get(qualifier)
                            .apply(singleton);
                    if (value == null)
                        throw new ResolutionException(
                                "Product " + product.getName() + " of " + owner.getName() + " is null after load");
                    products.computeIfAbsent(product, key -> new HashMap<>()).put(qualifier, value);
                }));
    }

    // Purges partially captured products so a failed materialization leaves no stale entries
    private void dropProducts(Class<?> owner) {
        registry.productOwners()
                .forEach((product, qualified) -> qualified.forEach((qualifier, type) -> {
                    if (!type.equals(owner)) return;
                    Map<String, Object> captured = products.get(product);
                    if (captured != null) captured.remove(qualifier);
                }));
    }
}
