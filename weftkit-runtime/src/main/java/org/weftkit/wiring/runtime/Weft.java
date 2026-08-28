package org.weftkit.wiring.runtime;

import java.util.List;

/**
 * Enumerates the components of a loaded graph, injectable via a {@code Weft} constructor
 * parameter. This is the one capability constructor injection cannot express, since the set is
 * not statically known; everything else a component needs, it injects directly.
 */
public final class Weft {

    private final WeftLoader loader;

    Weft(WeftLoader loader) {
        this.loader = loader;
    }

    /**
     * Every enabled component assignable to the supertype, sorted by class name. Components gated
     * off by {@code @FeatureFlag} are skipped without being constructed. Rejects {@code Object},
     * which would instantiate the whole graph.
     */
    public <T> List<T> all(Class<T> supertype) {
        if (supertype == Object.class)
            throw new ResolutionException("Refusing to enumerate the whole graph, pass a specific supertype");
        return loader.createAll(supertype);
    }
}
