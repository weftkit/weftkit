package org.weftkit.wiring.runtime;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.weftkit.wiring.Loader;
import org.weftkit.wiring.registry.CoreRegistry;
import org.weftkit.wiring.registry.Dependency;
import org.weftkit.wiring.runtime.exception.ComponentLoadException;
import org.weftkit.wiring.runtime.exception.ResolutionException;

/**
 * The wired component core: builds components, caches singletons, runs {@link Loader} hooks, and
 * keeps the bookkeeping that unload and diagnostics read. Sees only the {@link CoreRegistry}.
 * Features layer on through registered {@link Scope}s, {@link CreationListener}s, and
 * {@link #connect}. Singleton lifecycle runs under the graph lock, prototype builds, the
 * singleton cache, and the timings run without it.
 */
final class Components implements Instantiator {

    private final CoreRegistry registry;

    private final ReentrantLock graph = new ReentrantLock();

    // Graph lock only, includes constructors and lifecycle observers.
    private int singletonLoads;

    // Scope per registered type, eager built in. A type with no entry is a per-call prototype
    private final Map<Class<?>, Scope> scopes = new HashMap<>();

    private final Map<Class<?>, Object> singletons = new ConcurrentHashMap<>();

    // Graph lock only
    private final Map<Class<?>, Object> loading = new HashMap<>();

    // Completion order of singleton creation, eager and lazy interleaved, driving reverse-order
    // unload. Insertion order of the singletons map would not do: a hook-materialized lazy
    // singleton is inserted after its consumer but completes before it. Graph lock only
    private final List<Class<?>> creationOrder = new ArrayList<>();

    // Never clear in place, teardown replaces it
    private volatile Queue<Timing> timings = new ConcurrentLinkedQueue<>();

    private final List<CreationListener> listeners = new ArrayList<>();

    private DependencyResolver resolution;

    private FlagGate gate;

    private Runnable admission;

    Components(CoreRegistry registry) {
        this.registry = registry;
        Scope eager = (type, arguments) -> loaded(type);
        for (Class<?> type : registry.loadOrder()) scopes.put(type, eager);
    }

    // The resolver and the gate call back into the core, so they attach after construction
    void connect(DependencyResolver resolution, FlagGate gate, Runnable admission) {
        this.resolution = resolution;
        this.gate = gate;
        this.admission = admission;
    }

    // A feature registers the scope for its types, e.g. the lazy scope. Eager types are built in
    // and keep precedence, so a registry listing a type as both eager and lazy stays eager
    void scope(Class<?> type, Scope scope) {
        scopes.putIfAbsent(type, scope);
    }

    /** Runs the admission check. Under the graph lock, before a scope starts a singleton load. */
    void admit() {
        admission.run();
    }

    // Feature listeners attach after construction, since they may call back into the core
    void observe(CreationListener... additions) {
        listeners.addAll(Arrays.asList(additions));
    }

    // Lets a feature discover implementations without depending on the registry
    Set<Class<?>> wiredTypes() {
        return registry.parameters().keySet();
    }

    /** Runs the action under the graph lock. Reentrant. */
    <T> T locked(Supplier<T> action) {
        graph.lock();
        try {
            return action.get();
        } finally {
            graph.unlock();
        }
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
        RuntimeException failure = locked(this::teardown);
        if (failure != null) throw failure;
    }

    private RuntimeException teardown() {
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
        timings = new ConcurrentLinkedQueue<>();
        listeners.forEach(CreationListener::reset);
        gate.reset();
        return failure;
    }

    /**
     * The cached singleton, interface bindings applied, or null when none has been created.
     * Lock-free, a lazy singleton another thread is still loading is not visible yet.
     */
    Object cached(Class<?> type) {
        return created(bound(type, ""));
    }

    /**
     * The raw singleton without binding resolution, or null. The thread holding the graph lock
     * also sees the singleton it is still loading, so a load hook can resolve its own singleton
     * and products.
     */
    @Override
    public Object created(Class<?> type) {
        Object singleton = singletons.get(type);
        if (singleton != null || !graph.isHeldByCurrentThread()) return singleton;
        return loading.get(type);
    }

    Map<Class<?>, Duration> timingsSnapshot() {
        Map<Class<?>, Duration> snapshot = new LinkedHashMap<>();
        for (Timing timing : timings) snapshot.put(timing.type(), timing.duration());
        return Collections.unmodifiableMap(snapshot);
    }

    Duration totalTime() {
        return timings.stream().map(Timing::duration).reduce(Duration.ZERO, Duration::plus);
    }

    @Override
    public Object instance(Class<?> type, Object... arguments) {
        Scope scope = scopes.get(type);
        return scope != null ? scope.obtain(type, arguments) : build(type, arguments);
    }

    /**
     * Builds a singleton and runs its managed lifecycle: load hook, publish, record for unload,
     * notify listeners, and time it. Returns false when the load hook cleanly declines. A thrown
     * failure rolls back only this singleton, wider teardown stays with the caller. Shared by the
     * eager loop and the lazy scope.
     */
    boolean loadSingleton(Class<?> type, Object... arguments) {
        return locked(() -> {
            singletonLoads++;
            try {
                return load(type, arguments);
            } finally {
                singletonLoads--;
            }
        });
    }

    /** Caller holds the graph lock; another thread's creation has already finished by now. */
    void requireNoSingletonLoad() {
        if (singletonLoads != 0)
            throw new IllegalStateException("Cannot unload from a singleton constructor or load hook");
    }

    private boolean load(Class<?> type, Object[] arguments) {
        long start = System.nanoTime();
        Object singleton = build(type, arguments);
        loading.put(type, singleton);
        boolean recorded = false;
        int notified = 0;
        try {
            if (singleton instanceof Loader loader && !loader.load()) return false;
            creationOrder.add(type);
            recorded = true;
            for (CreationListener listener : listeners) {
                listener.onLoaded(type, singleton);
                notified++;
            }
            // Publish only after every lifecycle observer has accepted the instance.
            singletons.put(type, singleton);
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
                singletons.remove(type);
                // The thrower left no partial state behind, so it needs no drop
                for (int i = 0; i < notified; i++) listeners.get(i).onDropped(type);
            }
            throw ex;
        } finally {
            loading.remove(type);
        }
        timings.add(new Timing(type, Duration.ofNanos(System.nanoTime() - start)));
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

    // Never take the graph lock here, prototypes build on any thread while a load hook runs
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
        Object singleton = created(type);
        if (singleton == null) throw new ResolutionException("Singleton dependency is not loaded: " + type.getName());
        return singleton;
    }

    private record Timing(Class<?> type, Duration duration) {}
}
