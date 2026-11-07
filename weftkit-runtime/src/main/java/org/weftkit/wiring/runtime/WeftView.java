package org.weftkit.wiring.runtime;

import java.util.List;
import org.weftkit.wiring.runtime.exception.ResolutionException;

/**
 * Live graph queries, injectable alongside {@link Weft}. Use {@link WeftDiagnostics} for
 * timings and state snapshots without evaluating flags. Shares the loader's threading contract: {@link #contains}, {@link #loadOrder}, and {@link #modules} work from any
 * thread at any time, the rest once the graph is loaded.
 */
public final class WeftView {

    private final WeftLoader loader;

    WeftView(WeftLoader loader) {
        this.loader = loader;
    }

    /**
     * The wired component classes assignable to the supertype, sorted by class name, feature
     * flagged ones excluded. The read-only mirror of {@link Weft#all}.
     * Rejects {@code Object}.
     */
    public <T> List<Class<? extends T>> types(Class<T> supertype) {
        if (supertype == Object.class)
            throw new ResolutionException("Refusing to enumerate the whole graph, pass a specific supertype");
        return loader.types(supertype);
    }

    /** Whether the type resolves to a wired component, interface bindings applied. */
    public boolean contains(Class<?> type) {
        return loader.contains(type);
    }

    /** Whether the type is a wired component not gated off by a {@code @FeatureFlag}. */
    public boolean enabled(Class<?> type) {
        return loader.componentEnabled(type);
    }

    /** The eager singleton load order. */
    public List<Class<?>> loadOrder() {
        return loader.loadOrder();
    }

    /** The {@code @WeftModule} classes woven into the graph, in load order. */
    public List<Class<?>> modules() {
        return loader.registry().modules();
    }
}
