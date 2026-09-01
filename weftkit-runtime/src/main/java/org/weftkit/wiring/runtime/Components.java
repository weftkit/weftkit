package org.weftkit.wiring.runtime;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.weftkit.wiring.Loader;
import org.weftkit.wiring.registry.CoreRegistry;
import org.weftkit.wiring.registry.Dependency;
import org.weftkit.wiring.runtime.exception.ComponentLoadException;
import org.weftkit.wiring.runtime.exception.ResolutionException;

/**
 * The wired component core: builds components, caches singletons, runs {@link Loader} hooks, and
 * keeps the bookkeeping that unload and diagnostics read. Sees only the {@link CoreRegistry}.
 * Features layer on through registered {@link Scope}s, {@link CreationListener}s, and
 * {@link #connect}.
 */
final class Components implements Instantiator {

    private final CoreRegistry registry;

    // Scope per registered type, eager built in. A type with no entry is a per-call prototype
    private final Map<Class<?>, Scope> scopes = new HashMap<>();

    private final Map<Class<?>, Object> singletons = new HashMap<>();

    // Completion order of singleton creation, eager and lazy interleaved, driving reverse-order
    // unload. Insertion order of the singletons map would not do: a hook-materialized lazy
    // singleton is inserted after its consumer but completes before it
    private final List<Class<?>> creationOrder = new ArrayList<>();

    private final Map<Class<?>, Duration> timings = new LinkedHashMap<>();

    private final List<CreationListener> listeners = new ArrayList<>();

    private DependencyResolver resolution;

    private FlagGate gate;

    Components(CoreRegistry registry) {
        this.registry = registry;
        Scope eager = (type, arguments) -> loaded(type);
        for (Class<?> type : registry.loadOrder()) scopes.put(type, eager);
    }

    // The resolver and the gate call back into the core, so they attach after construction
    void connect(DependencyResolver resolution, FlagGate gate) {
        this.resolution = resolution;
        this.gate = gate;
    }

    // A feature registers the scope for its types, e.g. the lazy scope. Eager types are built in
    // and keep precedence, so a registry listing a type as both eager and lazy stays eager
    void scope(Class<?> type, Scope scope) {
        scopes.putIfAbsent(type, scope);
    }

    // Feature listeners attach after construction, since they may call back into the core
    void observe(CreationListener... additions) {
        listeners.addAll(Arrays.asList(additions));
    }

    // Lets a feature discover implementations without depending on the registry
    Set<Class<?>> wiredTypes() {
        return registry.parameters().keySet();
    }

    boolean loadEager() {
        for (Class<?> type : registry.loadOrder()) {
            // A singleton gated off by its feature flag is never constructed
            if (!gate.enabled(type)) continue;
            try {
                // A cleanly declined load hook aborts the whole graph rather than failing one type
                if (!loadSingleton(type)) {
                    unloadAll();
                    return false;
                }
            } catch (RuntimeException ex) {
                try {
                    unloadAll();
                } catch (RuntimeException teardown) {
                    ex.addSuppressed(teardown);
                }
                throw ex;
            }
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
        listeners.forEach(CreationListener::reset);
        gate.reset();
        if (failure != null) throw failure;
    }

    /** The cached singleton, interface bindings applied, or null when none has been created. */
    Object cached(Class<?> type) {
        return singletons.get(bound(type, ""));
    }

    /** The raw singleton without binding resolution, or null. */
    @Override
    public Object created(Class<?> type) {
        return singletons.get(type);
    }

    Map<Class<?>, Duration> timingsSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(timings));
    }

    Duration totalTime() {
        return timings.values().stream().reduce(Duration.ZERO, Duration::plus);
    }

    @Override
    public Object instance(Class<?> type, Object... arguments) {
        Scope scope = scopes.get(type);
        return scope != null ? scope.obtain(type, arguments) : build(type, arguments);
    }

    /**
     * Builds a singleton and runs its managed lifecycle: publish, load hook, record for unload,
     * notify listeners, and time it. Returns false when the load hook cleanly declines. A thrown
     * failure rolls back only this singleton, wider teardown stays with the caller. Shared by the
     * eager loop and the lazy scope.
     */
    boolean loadSingleton(Class<?> type, Object... arguments) {
        long start = System.nanoTime();
        Object singleton = build(type, arguments);
        // Publish before the load hook runs so the hook can resolve this singleton and its products
        singletons.put(type, singleton);
        boolean recorded = false;
        int notified = 0;
        try {
            if (singleton instanceof Loader loader && !loader.load()) {
                singletons.remove(type);
                return false;
            }
            creationOrder.add(type);
            recorded = true;
            for (CreationListener listener : listeners) {
                listener.onLoaded(type, singleton);
                notified++;
            }
        } catch (RuntimeException ex) {
            // Roll back only this singleton, the caller decides whether the wider graph comes down
            if (recorded) {
                if (singleton instanceof Loader loader) {
                    try {
                        loader.unload();
                    } catch (RuntimeException teardown) {
                        ex.addSuppressed(teardown);
                    }
                }
                creationOrder.remove(type);
                // The thrower left no partial state behind, so it needs no drop
                for (int i = 0; i < notified; i++) listeners.get(i).onDropped(type);
            }
            singletons.remove(type);
            throw ex;
        }
        timings.put(type, Duration.ofNanos(System.nanoTime() - start));
        return true;
    }

    @Override
    public Class<?> bound(Class<?> type, String qualifier) {
        Map<String, Class<?>> bindings = registry.bindings().get(type);
        Class<?> implementation = bindings == null ? null : bindings.get(qualifier);
        if (implementation != null) return implementation;
        return qualifier.isEmpty() ? type : null;
    }

    @Override
    public boolean wired(Class<?> type) {
        return registry.parameters().containsKey(type);
    }

    private Object build(Class<?> target, Object... arguments) {
        // A listener may need to run before the target, e.g. to fill required static holders
        for (CreationListener listener : listeners) listener.onBuilding(target);
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
}
