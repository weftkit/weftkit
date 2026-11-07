package org.weftkit.wiring.runtime;

import java.lang.annotation.Annotation;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.weftkit.wiring.FeatureFlags;
import org.weftkit.wiring.Loader;
import org.weftkit.wiring.registry.ComponentRegistry;
import org.weftkit.wiring.runtime.exception.ResolutionException;

/**
 * Instantiates and injects the components of a {@link ComponentRegistry}. Eager singletons are
 * created by {@link #load()} in dependency order, lazy singletons on their first injection, and
 * plain components fresh for every {@link #create}. A lazy singleton runs its {@link Loader}
 * hooks too: a false return or exception at materialization drops the singleton and propagates
 * to the injection site while the rest of the graph stays loaded. The failure sticks, further
 * injections rethrow cheaply until {@link #resetFailure} or {@link #unload}.
 *
 * <p>Threading. {@link #load}: creating thread only, on Bukkit the server main thread. Every
 * other call: creating thread only until {@code load()} returned true, then any thread. Reads
 * are lock-free; singleton creation and teardown serialize on an internal lock, so a lazy load
 * hook runs once, on the thread that demanded it first, and a loaded lazy singleton
 * resolves lock-free. Unloading waits for a lazy materialization in progress and for nothing
 * else. Already-admitted cache reads and prototype creation may finish after unload; they can
 * observe removed dependencies or retain instances whose unload hooks have run. Callers must
 * quiesce their own work if it needs those resources alive. During teardown, only the unloading
 * thread may start new graph calls. A late materialization on another thread throws instead of
 * reviving the graph. Never block on the owner thread inside a lazy load hook, unload waits for
 * the hook there. Not
 * injectable, components reach the graph through {@link Weft} and {@link WeftView}.
 */
public final class WeftLoader {

    private enum State {
        UNLOADED,
        LOADING,
        LOADED,
        UNLOADING
    }

    private final ComponentRegistry registry;

    private final Thread owner = Thread.currentThread();

    private volatile State state = State.UNLOADED;

    private volatile Thread unloader;

    private final AmbientValues ambient;

    private final Weft weft = new Weft(this);

    private final WeftView view = new WeftView(this);

    private final WeftDiagnostics diagnostics = new WeftDiagnostics(this);

    private final Components components;

    private final LazyScope lazy;

    private final ModuleActivation modules;

    private final FlagGate gate;

    /**
     * Creates a loader over the registry. Every ambient value must match one of the registry's
     * declared ambient types, with {@code FeatureFlags} always declared implicitly, and each
     * declared type takes at most one value. A value matching no declared type or a second value
     * for a declared type is rejected here, before any component resolves.
     */
    public WeftLoader(ComponentRegistry registry, Object... ambient) {
        this.registry = registry;
        this.ambient = new AmbientValues(registry, ambient);
        this.components = new Components(registry);
        this.lazy = new LazyScope(components);
        this.modules = new ModuleActivation(registry, components);
        for (Class<?> type : registry.lazySingletons()) components.scope(type, lazy);
        Products products = new Products(registry);
        components.observe(products, lazy, modules, new Requirements(registry, components));
        this.gate = new FlagGate(
                registry,
                components::wiredTypes,
                () -> ambient(FeatureFlags.class),
                components::instance,
                components::locked);
        components.connect(
                new Resolution(registry, this.ambient, weft, view, diagnostics, components, gate, products),
                gate,
                this::requireLoadedOrOwner);
    }

    /**
     * Creates every eager singleton in dependency order, running their {@link Loader} hooks.
     * Returns false after tearing the loaded singletons back down when a hook aborts. An
     * unchecked exception or error thrown while creating or loading a singleton triggers the same teardown
     * before it propagates, with any teardown failure attached as suppressed. Throws when the
     * graph is already loaded, {@link #unload} first. Owner thread only.
     */
    public boolean load() {
        requireOwnerThread();
        if (state != State.UNLOADED)
            throw new IllegalStateException("WeftLoader is already "
                    + switch (state) {
                        case LOADED -> "loaded, unload it before loading again";
                        case UNLOADING -> "unloading";
                        default -> "loading";
                    });
        state = State.LOADING;
        boolean loaded = false;
        try {
            loaded = components.loadEager();
        } finally {
            state = loaded ? State.LOADED : State.UNLOADED;
        }
        return loaded;
    }

    /**
     * Runs the {@link Loader} teardown hooks in reverse creation order, lazy singletons
     * interleaved where they materialized, and drops every cached singleton. Teardown continues
     * past failures, rethrowing the first with the rest suppressed. Safe to call repeatedly on
     * the owner thread, any other thread may call it only while the graph is loaded. A
     * component materialized by an unload hook during teardown is dropped without its own hook,
     * but its owned resources still close. Owned resources close before each singleton's hook.
     * Waits for a lazy materialization in progress. The hooks run on the calling thread, which
     * keeps graph access for their duration. Reentrant unload during loading, singleton creation,
     * or teardown is rejected.
     */
    public void unload() {
        requireLoadedOrOwner();
        components.locked(() -> {
            requireLoadedOrOwner();
            if (state == State.LOADING || state == State.UNLOADING)
                throw new IllegalStateException(
                        "Cannot unload while the graph is " + state.name().toLowerCase(java.util.Locale.ROOT));
            components.requireNoSingletonLoad();
            modules.requireIdle();
            unloader = Thread.currentThread();
            state = State.UNLOADING;
            try {
                Throwable failure = null;
                try {
                    modules.deactivate();
                } catch (RuntimeException | Error ex) {
                    failure = ex;
                }
                try {
                    components.unloadAll();
                } catch (RuntimeException | Error teardown) {
                    if (failure == null) throw teardown;
                    if (failure != teardown) failure.addSuppressed(teardown);
                }
                if (failure instanceof Error error) throw error;
                if (failure != null) throw (RuntimeException) failure;
            } finally {
                unloader = null;
                state = State.UNLOADED;
            }
            return null;
        });
    }

    /** The same diagnostics facade supplied by constructor injection. */
    public WeftDiagnostics diagnostics() {
        return diagnostics;
    }

    WeftDiagnostics.Snapshot diagnosticSnapshot() {
        requireLoadedOrOwner();
        return components.locked(() -> {
            requireLoadedOrOwner();
            return diagnostics.capture(
                    registry,
                    components,
                    lazy,
                    gate,
                    WeftDiagnostics.GraphState.valueOf(state.name()),
                    modules.diagnosticState());
        });
    }

    boolean activateModules() {
        requireLoadedOrOwner();
        return components.locked(() -> {
            requireLoadedOrOwner();
            if (state != State.LOADED) throw new IllegalStateException("Load the graph before activating modules");
            return modules.activate();
        });
    }

    void deactivateModules() {
        requireLoadedOrOwner();
        components.locked(() -> {
            requireLoadedOrOwner();
            modules.deactivate();
            return null;
        });
    }

    /**
     * Clears the failure marker of a lazy singleton, resolving interface bindings, so the next
     * injection retries. Returns whether a marker was set.
     */
    public boolean resetFailure(Class<?> type) {
        requireLoadedOrOwner();
        return lazy.resetFailure(components.bound(type, ""));
    }

    /**
     * Returns the cached singleton for the type, resolving interface bindings, or null when none
     * has completed loading. Lock-free: a lazy singleton another thread is still loading is not
     * visible yet, {@link #create} waits for it instead.
     */
    public <T> T get(Class<T> type) {
        requireLoadedOrOwner();
        return type.cast(components.cached(type));
    }

    /**
     * Returns the ambient value whose declared type is assignable to the type, or null when no
     * declared type matches or its value is absent. Throws when several declared types match with
     * different values.
     */
    public <T> T ambient(Class<T> type) {
        return type.cast(ambient.match(type));
    }

    /** Returns the registry this loader drives. */
    public ComponentRegistry registry() {
        return registry;
    }

    /** Returns the eager singleton load order. */
    public List<Class<?>> loadOrder() {
        return registry.loadOrder();
    }

    /**
     * Returns how long each singleton took to create and load, in creation order. An eager
     * entry includes any lazy materialization its creation triggered. A snapshot at call time,
     * safe to hand to another thread.
     */
    public Map<Class<?>, Duration> loadTimings() {
        requireLoadedOrOwner();
        return components.timingsSnapshot();
    }

    /** Returns the combined creation and load time of every singleton created so far. */
    public Duration totalLoadTime() {
        requireLoadedOrOwner();
        return components.totalTime();
    }

    /**
     * Creates or fetches every enabled component assignable to the supertype, sorted by class
     * name. Components gated off by their feature flag are skipped without being constructed.
     * Arguments are matched to constructor parameters by type, the first assignable argument
     * wins, and arguments a component cannot use are ignored. They only reach plain components,
     * since a singleton's cached state must not depend on whichever call materialized it.
     */
    public <T> List<T> createAll(Class<T> supertype, Object... arguments) {
        requireLoadedOrOwner();
        return enabled(registry.parameters().keySet().stream(), supertype)
                .map(type -> supertype.cast(components.instance(type, arguments)))
                .toList();
    }

    <T, A extends Annotation> List<Annotated<T, A>> createAnnotated(Class<T> supertype, Class<A> marker) {
        requireLoadedOrOwner();
        Map<Class<?>, List<A>> annotated = new HashMap<>();
        registry.annotations().forEach((type, annotations) -> {
            List<A> matching = annotations.stream()
                    .filter(marker::isInstance)
                    .map(marker::cast)
                    .toList();
            if (!matching.isEmpty()) annotated.put(type, matching);
        });
        return enabled(annotated.keySet().stream(), supertype)
                .flatMap(type -> {
                    T component = supertype.cast(components.instance(type));
                    return annotated.get(type).stream().map(annotation -> new Annotated<>(component, annotation));
                })
                .toList();
    }

    Stream<Annotation> enabledAnnotations() {
        requireLoadedOrOwner();
        return registry.annotations().entrySet().stream()
                .filter(entry -> gate.enabled(entry.getKey()))
                .flatMap(entry -> entry.getValue().stream());
    }

    // Read-only counterpart of createAll, surfaced through WeftView
    <T> List<Class<? extends T>> types(Class<T> supertype) {
        requireLoadedOrOwner();
        return enabled(registry.parameters().keySet().stream(), supertype)
                .<Class<? extends T>>map(type -> type.asSubclass(supertype))
                .toList();
    }

    private Stream<Class<?>> enabled(Stream<Class<?>> types, Class<?> supertype) {
        return types.filter(supertype::isAssignableFrom)
                .filter(gate::enabled)
                .sorted(Comparator.comparing(Class::getName));
    }

    boolean contains(Class<?> type) {
        Class<?> target = components.bound(type, "");
        return target != null && components.wired(target);
    }

    boolean componentEnabled(Class<?> type) {
        requireLoadedOrOwner();
        Class<?> target = components.bound(type, "");
        return target != null && components.wired(target) && gate.enabled(target);
    }

    /**
     * Creates a component of the type, resolving interface bindings. Singletons resolve to their
     * cached instance, lazy ones materializing on demand, and passing them arguments throws since
     * arguments never reach a singleton. Plain components are built fresh, each constructor
     * parameter resolved from the given arguments, the ambient values, and the graph. Among
     * several assignable arguments the first given wins. Throws for a component gated off by its
     * feature flag.
     */
    public <T> T create(Class<T> type, Object... arguments) {
        requireLoadedOrOwner();
        Class<?> target = components.bound(type, "");
        if (target != null && !gate.enabled(target))
            throw new ResolutionException("Component is disabled by its feature flag: " + target.getName());
        if (arguments.length > 0 && singleton(target))
            throw new ResolutionException(
                    "Arguments never reach a singleton, drop them or make the component plain: " + target.getName());
        return type.cast(components.instance(target, arguments));
    }

    private boolean singleton(Class<?> type) {
        return registry.loadOrder().contains(type) || registry.lazySingletons().contains(type);
    }

    private void requireOwnerThread() {
        if (Thread.currentThread() == owner) return;
        throw confinement("Loading stays on the owner thread");
    }

    private void requireLoadedOrOwner() {
        Thread current = Thread.currentThread();
        State observed = state;
        if (observed == State.LOADED || current == unloader || (current == owner && observed != State.UNLOADING))
            return;
        throw confinement(
                switch (observed) {
                    case LOADING -> "The graph is still loading, schedule graph access back on the owner thread";
                    case UNLOADING -> "The graph is unloading, only the unloading thread may touch it";
                    default -> "The graph is not loaded, only the owner thread may touch it";
                });
    }

    private IllegalStateException confinement(String detail) {
        return new IllegalStateException("WeftLoader is confined to its creating thread '" + owner.getName()
                + "' but was accessed from '" + Thread.currentThread().getName() + "'. " + detail);
    }
}
