package org.weftkit.wiring.runtime;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.weftkit.wiring.registry.ComponentRegistry;
import org.weftkit.wiring.registry.Flag;

/** Injectable diagnostics for one graph. Snapshots never construct components or evaluate flags. */
public final class WeftDiagnostics {
    public enum GraphState {
        UNLOADED,
        LOADING,
        LOADED,
        UNLOADING
    }

    public enum Scope {
        EAGER,
        LAZY,
        PROTOTYPE
    }

    /** Singleton lifecycle state; prototype instances are not tracked. */
    public enum ComponentState {
        NOT_CREATED,
        LOADING,
        LOADED,
        FAILED,
        PROTOTYPE
    }

    public enum ModuleState {
        READY,
        ACTIVATING,
        ACTIVE,
        DEACTIVATING,
        FINISHED
    }

    /** Copied exception type and nullable message; retains no throwable references. */
    public record Failure(String type, String message) {}

    /**
     * Registered component state. Enabled is true if unflagged, the last checked flag decision,
     * or empty if unchecked. Timings cover successful singleton loads; failures are sticky lazy failures.
     */
    public record Component(
            Class<?> type,
            Scope scope,
            ComponentState state,
            boolean module,
            Optional<Flag> flag,
            Optional<Boolean> enabled,
            Optional<Duration> loadTime,
            Optional<Failure> failure) {}

    /**
     * Immutable snapshot, sorted by component class name. Concurrent flag observations are not
     * atomic with singleton state. Total load time can count nested lazy loads twice.
     */
    public record Snapshot(
            GraphState state,
            ModuleState moduleState,
            List<Component> components,
            List<Class<?>> loadOrder,
            Duration totalLoadTime) {
        public Snapshot {
            components = List.copyOf(components);
            loadOrder = List.copyOf(loadOrder);
        }
    }

    private final WeftLoader loader;

    WeftDiagnostics(WeftLoader loader) {
        this.loader = loader;
    }

    /**
     * Captures under the graph lock using {@link WeftLoader}'s threading contract. May wait for singleton
     * creation or teardown; hooks must not wait for another thread to capture a snapshot.
     *
     * @throws IllegalStateException if the calling thread is not admitted by the loader
     */
    public Snapshot snapshot() {
        return loader.diagnosticSnapshot();
    }

    // The loader holds the graph lock and checks admission before capture.
    Snapshot capture(
            ComponentRegistry registry,
            Components core,
            LazyScope lazy,
            FlagGate gate,
            GraphState state,
            ModuleState moduleState) {
        Set<Class<?>> eager = Set.copyOf(registry.loadOrder());
        Set<Class<?>> modules = Set.copyOf(registry.modules());
        Map<Class<?>, Duration> timings = core.timingsSnapshot();
        List<Component> entries = registry.parameters().keySet().stream()
                .sorted(Comparator.comparing(Class::getName))
                .map(type -> {
                    Scope scope = scope(type, eager, registry.lazySingletons());
                    Optional<Failure> failure = Optional.ofNullable(lazy.failure(type))
                            .map(ex -> new Failure(ex.getClass().getName(), ex.getMessage()));
                    ComponentState status = failure.isPresent() ? ComponentState.FAILED : core.diagnosticState(type);
                    return new Component(
                            type,
                            scope,
                            status,
                            modules.contains(type),
                            Optional.ofNullable(registry.featureFlags().get(type)),
                            Optional.ofNullable(gate.observed(type)),
                            Optional.ofNullable(timings.get(type)),
                            failure);
                })
                .toList();
        Duration total = timings.values().stream().reduce(Duration.ZERO, Duration::plus);
        return new Snapshot(state, moduleState, entries, registry.loadOrder(), total);
    }

    private static Scope scope(Class<?> type, Set<Class<?>> eager, Set<Class<?>> lazy) {
        if (eager.contains(type)) return Scope.EAGER;
        return lazy.contains(type) ? Scope.LAZY : Scope.PROTOTYPE;
    }
}
