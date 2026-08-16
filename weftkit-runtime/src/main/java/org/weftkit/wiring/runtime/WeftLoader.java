package org.weftkit.wiring.runtime;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.weftkit.wiring.Loader;

/**
 * Instantiates and injects the components of a {@link ComponentRegistry}. Eager singletons are
 * created by {@link #load()} in dependency order, lazy singletons on their first injection, and
 * plain components fresh for every {@link #create}. A lazy singleton runs its {@link Loader}
 * hooks too: {@code load()} at materialization, where a false return or exception drops the
 * singleton and propagates to the injection site while the rest of the graph stays loaded, so
 * the next injection retries. Not thread safe: drive it from a single thread, on Bukkit the
 * server main thread. The loader injects itself, so a component may declare a {@code WeftLoader}
 * constructor parameter to reach the graph at runtime.
 */
public final class WeftLoader {

    private final ComponentRegistry registry;

    private final Object[] ambient;

    private final Set<Class<?>> eager;

    private final Map<Class<?>, Object> singletons = new HashMap<>();

    // Completion order of singleton creation, eager and lazy interleaved, driving reverse-order
    // unload. Insertion order of the singletons map would not do: a hook-materialized lazy
    // singleton is inserted after its consumer but completes before it
    private final List<Class<?>> creationOrder = new ArrayList<>();

    private final Map<Class<?>, Duration> timings = new LinkedHashMap<>();

    private final Map<Class<?>, Map<String, Object>> products = new HashMap<>();

    /**
     * Creates a loader over the registry. The ambient values are offered to every component by
     * type, before the graph resolves, and at most one value may match a parameter type.
     */
    public WeftLoader(ComponentRegistry registry, Object... ambient) {
        this.registry = registry;
        this.ambient = ambient.clone();
        this.eager = Set.copyOf(registry.loadOrder());
    }

    /**
     * Creates every eager singleton in dependency order, running their {@link Loader} hooks.
     * Returns false after tearing the loaded singletons back down when a hook aborts. A
     * RuntimeException thrown while creating or loading a singleton triggers the same teardown
     * before it propagates, with any teardown failure attached as suppressed.
     */
    public boolean load() {
        for (Class<?> type : registry.loadOrder()) {
            long start = System.nanoTime();
            boolean hookCompleted = false;
            try {
                Object singleton = build(type);
                // Publish before the load hook runs so the hook can resolve this singleton and its products
                singletons.put(type, singleton);
                if (singleton instanceof Loader loader && !loader.load()) {
                    singletons.remove(type);
                    unload();
                    return false;
                }
                hookCompleted = true;
                creationOrder.add(type);
                captureProducts(type, singleton);
            } catch (RuntimeException ex) {
                // A singleton whose hook never completed is treated like an abort and not unloaded
                if (!hookCompleted) singletons.remove(type);
                try {
                    unload();
                } catch (RuntimeException teardown) {
                    ex.addSuppressed(teardown);
                }
                throw ex;
            }
            timings.put(type, Duration.ofNanos(System.nanoTime() - start));
        }
        return true;
    }

    /**
     * Runs the {@link Loader} teardown hooks in reverse creation order, lazy singletons
     * interleaved where they materialized, and drops every cached singleton. Teardown continues
     * past failures, rethrowing the first with the rest suppressed. Safe to call repeatedly. A
     * component materialized by an unload hook during teardown is dropped without its own hook.
     */
    public void unload() {
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
        if (failure != null) throw failure;
    }

    /**
     * Returns the cached singleton for the type, resolving interface bindings, or null when none
     * has been created.
     */
    public <T> T get(Class<T> type) {
        return type.cast(singletons.get(bound(type, "")));
    }

    /** Returns the eager singleton load order. */
    public List<Class<?>> loadOrder() {
        return registry.loadOrder();
    }

    /**
     * Returns how long each singleton took to create and load, in creation order. An eager
     * entry includes the time of any lazy materialization its own creation triggered.
     */
    public Map<Class<?>, Duration> loadTimings() {
        return Collections.unmodifiableMap(timings);
    }

    /** Returns the combined creation and load time of every singleton created so far. */
    public Duration totalLoadTime() {
        return timings.values().stream().reduce(Duration.ZERO, Duration::plus);
    }

    /**
     * Creates or fetches every component assignable to the supertype, sorted by class name.
     * Arguments are matched to constructor parameters by type; they only reach plain components,
     * since a singleton's cached state must not depend on whichever call materialized it.
     */
    public <T> List<T> createAll(Class<T> supertype, Object... arguments) {
        return registry.parameters().keySet().stream()
                .filter(supertype::isAssignableFrom)
                .sorted(Comparator.comparing(Class::getName))
                .map(type -> supertype.cast(instance(type, arguments)))
                .toList();
    }

    /**
     * Creates a component of the type, resolving interface bindings. Singletons resolve to their
     * cached instance, lazy ones materializing on demand. Plain components are built fresh, each
     * constructor parameter resolved from the given arguments, the ambient values, and the graph.
     */
    public <T> T create(Class<T> type, Object... arguments) {
        return type.cast(instance(bound(type, ""), arguments));
    }

    private Object build(Class<?> target, Object... arguments) {
        // Initializers of required static holders must have filled them before the target runs
        for (Class<?> initializer : registry.requirements().getOrDefault(target, List.of())) instance(initializer);
        Object[] parameters = parameters(target).stream()
                .map(dependency -> resolve(dependency, arguments, target))
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

    private Object resolve(Dependency dependency, Object[] arguments, Class<?> component) {
        Class<?> type = dependency.type();
        // Arguments, ambient roots, and the loader itself carry no qualifier, so only an unqualified
        // dependency may match them; a qualified one must resolve through its binding or product
        if (dependency.qualifier().isEmpty()) {
            Object argument = unique(arguments, type, component, "argument");
            if (argument != null) return argument;
            Object provided = unique(ambient, type, component, "ambient value");
            if (provided != null) return provided;
            if (type.isInstance(this)) return this;
        }
        Class<?> target = bound(type, dependency.qualifier());
        if (target != null && registry.parameters().containsKey(target)) return instance(target);
        Object product = product(type, dependency);
        if (product != null) return product;
        if (dependency.optional()) return null;
        throw new ResolutionException("Cannot resolve dependency " + type.getName() + " for " + component.getName());
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

    private Object instance(Class<?> type, Object... arguments) {
        if (eager.contains(type)) return loaded(type);
        // The cached instance must not depend on one call site, so lazy singletons ignore arguments
        if (registry.lazySingletons().contains(type)) return lazySingleton(type);
        return build(type, arguments);
    }

    private Object lazySingleton(Class<?> type) {
        Object singleton = singletons.get(type);
        if (singleton != null) return singleton;
        long start = System.nanoTime();
        Object created = build(type);
        // Publish before the load hook runs so the hook can resolve this singleton and its products
        singletons.put(type, created);
        boolean hookCompleted = false;
        try {
            if (created instanceof Loader loader && !loader.load()) throw new ComponentLoadException(type.getName());
            hookCompleted = true;
            creationOrder.add(type);
            captureProducts(type, created);
        } catch (RuntimeException ex) {
            // Only this singleton is dropped, the graph stays up and the next injection retries
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
            throw ex;
        }
        timings.put(type, Duration.ofNanos(System.nanoTime() - start));
        return created;
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

    private Object product(Class<?> type, Dependency dependency) {
        Map<String, Class<?>> owners = registry.productOwners().get(type);
        Class<?> owner = owners == null ? null : owners.get(dependency.qualifier());
        if (owner == null) return null;
        Map<String, Object> captured = products.get(type);
        Object cached = captured == null ? null : captured.get(dependency.qualifier());
        if (cached != null) return cached;
        // The owner may still be inside its own load hook, so fall back to the live getter
        Object provider = singletons.get(owner);
        // Deliberate for optional product dependencies too, they materialize instead of staying empty
        if (provider == null && registry.lazySingletons().contains(owner)) provider = lazySingleton(owner);
        Object product = provider == null
                ? null
                : registry.productGetters()
                        .get(type)
                        .get(dependency.qualifier())
                        .apply(provider);
        if (product == null && !dependency.optional())
            throw new ResolutionException("Dependency is not available yet: " + type.getName());
        return product;
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

    private Class<?> bound(Class<?> type, String qualifier) {
        Map<String, Class<?>> bindings = registry.bindings().get(type);
        Class<?> implementation = bindings == null ? null : bindings.get(qualifier);
        if (implementation != null) return implementation;
        return qualifier.isEmpty() ? type : null;
    }

    private Object loaded(Class<?> type) {
        Object singleton = singletons.get(type);
        if (singleton == null) throw new ResolutionException("Singleton dependency is not loaded: " + type.getName());
        return singleton;
    }
}
