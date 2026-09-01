package org.weftkit.wiring.runtime;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
 * injections rethrow cheaply until {@link #resetFailure} or {@link #unload}. Confined to its
 * creating thread, on Bukkit the server main thread: calls touching mutable state fail fast
 * from other threads, only immutable registry views are thread safe. Not injectable, components
 * reach the graph through {@link Weft} and {@link WeftView}.
 */
public final class WeftLoader {

    private final ComponentRegistry registry;

    private final Thread owner = Thread.currentThread();

    private final AmbientValues ambient;

    private final Weft weft = new Weft(this);

    private final WeftView view = new WeftView(this);

    private final Components components;

    private final LazyScope lazy;

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
        for (Class<?> type : registry.lazySingletons()) components.scope(type, lazy);
        Products products = new Products(registry);
        components.observe(products, lazy, new Requirements(registry, components));
        this.gate =
                new FlagGate(registry, components::wiredTypes, () -> ambient(FeatureFlags.class), components::instance);
        components.connect(new Resolution(registry, this.ambient, weft, view, components, gate, products), gate);
    }

    /**
     * Creates every eager singleton in dependency order, running their {@link Loader} hooks.
     * Returns false after tearing the loaded singletons back down when a hook aborts. A
     * RuntimeException thrown while creating or loading a singleton triggers the same teardown
     * before it propagates, with any teardown failure attached as suppressed.
     */
    public boolean load() {
        requireOwnerThread();
        return components.loadEager();
    }

    /**
     * Runs the {@link Loader} teardown hooks in reverse creation order, lazy singletons
     * interleaved where they materialized, and drops every cached singleton. Teardown continues
     * past failures, rethrowing the first with the rest suppressed. Safe to call repeatedly. A
     * component materialized by an unload hook during teardown is dropped without its own hook.
     */
    public void unload() {
        requireOwnerThread();
        components.unloadAll();
    }

    /**
     * Clears the failure marker of a lazy singleton, resolving interface bindings, so the next
     * injection retries. Returns whether a marker was set.
     */
    public boolean resetFailure(Class<?> type) {
        requireOwnerThread();
        return lazy.resetFailure(components.bound(type, ""));
    }

    /**
     * Returns the cached singleton for the type, resolving interface bindings, or null when none
     * has been created.
     */
    public <T> T get(Class<T> type) {
        requireOwnerThread();
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
        requireOwnerThread();
        return components.timingsSnapshot();
    }

    /** Returns the combined creation and load time of every singleton created so far. */
    public Duration totalLoadTime() {
        requireOwnerThread();
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
        requireOwnerThread();
        return registry.parameters().keySet().stream()
                .filter(supertype::isAssignableFrom)
                .filter(gate::enabled)
                .sorted(Comparator.comparing(Class::getName))
                .map(type -> supertype.cast(components.instance(type, arguments)))
                .toList();
    }

    // Read-only counterpart of createAll, surfaced through WeftView. Still owner-thread only,
    // flag resolution may materialize the FeatureFlags singleton
    <T> List<Class<? extends T>> types(Class<T> supertype) {
        requireOwnerThread();
        List<Class<? extends T>> matches = new ArrayList<>();
        registry.parameters().keySet().stream()
                .filter(supertype::isAssignableFrom)
                .filter(gate::enabled)
                .sorted(Comparator.comparing(Class::getName))
                .forEach(type -> matches.add(type.asSubclass(supertype)));
        return matches;
    }

    boolean contains(Class<?> type) {
        Class<?> target = components.bound(type, "");
        return target != null && components.wired(target);
    }

    boolean componentEnabled(Class<?> type) {
        requireOwnerThread();
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
        requireOwnerThread();
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
        throw new IllegalStateException("WeftLoader is confined to its creating thread '" + owner.getName()
                + "' but was accessed from '" + Thread.currentThread().getName()
                + "'. Schedule graph access back on the owner thread");
    }
}
