package org.weftkit.wiring.processor.spi;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;

/**
 * A platform-specific validation rule applied to every {@code @Wired} component at compile time.
 * Implementations are discovered through {@link java.util.ServiceLoader}, so an adapter module can
 * contribute rules without the core processor depending on its platform. Report problems through
 * {@link ProcessingEnvironment#getMessager()}.
 */
public interface ComponentRule {

    void validate(TypeElement component, ProcessingEnvironment processingEnv);
}
