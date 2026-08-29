package org.weftkit.wiring.runtime;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * A read-only view of a loaded graph, injectable alongside {@link Weft}.
 */
public final class WeftView {

    private final WeftLoader loader;

    WeftView(WeftLoader loader) {
        this.loader = loader;
    }

    /**
     * The wired component classes assignable to the supertype, sorted by class name, feature
     * flagged ones excluded. The read-only mirror of {@link Weft#all}. Rejects {@code Object}.
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

    /** How long each singleton took to create and load, in creation order. */
    public Map<Class<?>, Duration> loadTimings() {
        return loader.loadTimings();
    }

    /** The combined create and load time of every singleton built so far. */
    public Duration totalLoadTime() {
        return loader.totalLoadTime();
    }

    /** The {@code @WeftModule} classes woven into the graph, in load order. */
    public List<Class<?>> modules() {
        return loader.registry().modules();
    }
}
