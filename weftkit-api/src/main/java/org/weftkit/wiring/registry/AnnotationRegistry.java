package org.weftkit.wiring.registry;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;

/**
 * The {@code @HandledBy} annotation data: each component's handled annotations, generated as plain
 * implementations so the runtime never reads annotations reflectively, and the binary name of the
 * module handling each annotation type. Both default to empty for hand-built registries. Modules
 * find their components only through {@link #annotations}, so a hand-built registry leaving it
 * empty leaves {@code @CommandHandler} and similar components inert.
 */
public interface AnnotationRegistry {

    default Map<Class<?>, List<Annotation>> annotations() {
        return Map.of();
    }

    default Map<Class<? extends Annotation>, String> handlers() {
        return Map.of();
    }
}
