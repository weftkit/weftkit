package org.weftkit.wiring.runtime;

import java.lang.annotation.Annotation;
import java.util.List;
import org.weftkit.wiring.runtime.exception.ResolutionException;

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

    /**
     * Every enabled component assignable to the supertype whose class carries the
     * {@code @HandledBy} annotation, paired with it and sorted by class name. A repeated
     * annotation pairs the component once per instance, in declaration order. Only those
     * components are constructed.
     */
    public <T, A extends Annotation> List<Annotated<T, A>> annotated(Class<T> supertype, Class<A> marker) {
        return loader.createAnnotated(supertype, marker);
    }
}
