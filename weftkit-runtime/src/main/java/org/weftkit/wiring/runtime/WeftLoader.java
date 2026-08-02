package org.weftkit.wiring.runtime;

import java.time.Duration;
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
 * plain components fresh for every {@link #create}. Not thread safe: drive it from a single
 * thread, on Bukkit the server main thread. The loader injects itself, so a component may declare
 * a {@code WeftLoader} constructor parameter to reach the graph at runtime.
 */
public final class WeftLoader {

    private final ComponentRegistry registry;

    private final Object[] ambient;

    private final Set<Class<?>> eager;

    private final Map<Class<?>, Object> singletons = new HashMap<>();

    private final Map<Class<?>, Duration> timings = new LinkedHashMap<>();

    public WeftLoader(ComponentRegistry registry, Object... ambient) {
        this.registry = registry;
        this.ambient = ambient.clone();
        this.eager = Set.copyOf(registry.loadOrder());
    }

    /**
     * Creates every eager singleton in dependency order, running their {@link Loader} hooks.
     * Returns false after tearing the loaded singletons back down when a hook aborts.
     */
    public boolean load() {
        for (Class<?> type : registry.loadOrder()) {
            long start = System.nanoTime();
            Object singleton = create(type);
            if (singleton instanceof Loader loader && !loader.load()) {
                unload();
                return false;
            }
            singletons.put(type, singleton);
            timings.put(type, Duration.ofNanos(System.nanoTime() - start));
        }
        return true;
    }

    /**
     * Runs the {@link Loader} teardown hooks in reverse load order and drops every cached
     * singleton. Teardown continues past failures, rethrowing the first with the rest suppressed.
     * Safe to call repeatedly.
     */
    public void unload() {
        List<Class<?>> order = registry.loadOrder();
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

    /** Returns how long each eager singleton took to create and load, in load order. */
    public Map<Class<?>, Duration> loadTimings() {
        return Collections.unmodifiableMap(timings);
    }

    /**
     * Creates or fetches every component assignable to the supertype, sorted by class name.
     * Arguments are matched to constructor parameters by type.
     */
    public <T> List<T> createAll(Class<T> supertype, Object... arguments) {
        return registry.parameters().keySet().stream()
                .filter(supertype::isAssignableFrom)
                .sorted(Comparator.comparing(Class::getName))
                .map(type -> supertype.cast(instance(type, arguments)))
                .toList();
    }

    /**
     * Builds a fresh component of the type, resolving each constructor parameter from the given
     * arguments, the ambient values, and the graph.
     */
    public <T> T create(Class<T> type, Object... arguments) {
        Class<?> target = bound(type, "");
        Object[] parameters = parameters(target).stream()
                .map(dependency -> resolve(dependency, arguments, target))
                .toArray();
        try {
            return type.cast(registry.factories().get(target).apply(parameters));
        } catch (RuntimeException ex) {
            throw new ComponentLoadException(target.getName(), ex);
        }
    }

    private List<Dependency> parameters(Class<?> type) {
        List<Dependency> parameters = registry.parameters().get(type);
        if (parameters == null)
            throw new IllegalStateException("Component is not annotated with @Wired: " + type.getName());
        return parameters;
    }

    private Object resolve(Dependency dependency, Object[] arguments, Class<?> component) {
        Class<?> type = dependency.type();
        Object match = null;
        for (Object argument : arguments) {
            if (!type.isInstance(argument)) continue;
            if (match != null)
                throw new IllegalStateException(
                        "Ambiguous argument for " + type.getName() + " of " + component.getName());
            match = argument;
        }
        if (match != null) return match;
        for (Object provided : ambient) if (type.isInstance(provided)) return provided;
        if (type.isInstance(this)) return this;
        Class<?> target = bound(type, dependency.qualifier());
        if (target != null && registry.parameters().containsKey(target)) return instance(target);
        Object product = product(type, dependency);
        if (product != null) return product;
        if (dependency.optional()) return null;
        throw new IllegalStateException("Cannot resolve dependency " + type.getName() + " for " + component.getName());
    }

    private Object instance(Class<?> type, Object... arguments) {
        if (eager.contains(type)) return loaded(type);
        if (registry.lazySingletons().contains(type)) return lazySingleton(type, arguments);
        return create(type, arguments);
    }

    private Object lazySingleton(Class<?> type, Object... arguments) {
        Object singleton = singletons.get(type);
        if (singleton != null) return singleton;
        Object created = create(type, arguments);
        singletons.put(type, created);
        return created;
    }

    private Object product(Class<?> type, Dependency dependency) {
        Map<String, Class<?>> owners = registry.productOwners().get(type);
        Class<?> owner = owners == null ? null : owners.get(dependency.qualifier());
        if (owner == null) return null;
        Object provider = singletons.get(owner);
        Object product = provider == null
                ? null
                : registry.productGetters()
                        .get(type)
                        .get(dependency.qualifier())
                        .apply(provider);
        if (product == null && !dependency.optional())
            throw new IllegalStateException("Dependency is not available yet: " + type.getName());
        return product;
    }

    private Class<?> bound(Class<?> type, String qualifier) {
        Map<String, Class<?>> bindings = registry.bindings().get(type);
        Class<?> implementation = bindings == null ? null : bindings.get(qualifier);
        if (implementation != null) return implementation;
        return qualifier.isEmpty() ? type : null;
    }

    private Object loaded(Class<?> type) {
        Object singleton = singletons.get(type);
        if (singleton == null) throw new IllegalStateException("Singleton dependency is not loaded: " + type.getName());
        return singleton;
    }
}
