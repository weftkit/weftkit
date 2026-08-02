package org.weftkit.wiring.processor.spi;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

/**
 * A platform-specific validation rule applied to every {@code @Wired} component at compile time.
 * Implementations are discovered through {@link java.util.ServiceLoader}, so an adapter module can
 * contribute rules without the core processor depending on its platform. Report problems through
 * {@link ProcessingEnvironment#getMessager()}, or the {@link #error} helper below.
 */
public interface ComponentRule {

    void validate(TypeElement component, ProcessingEnvironment processingEnv);

    /** Returns the mirror for the named type, or null when it is absent from the compile classpath. */
    default TypeMirror mirror(ProcessingEnvironment processingEnv, String qualifiedName) {
        TypeElement element = processingEnv.getElementUtils().getTypeElement(qualifiedName);
        return element == null ? null : element.asType();
    }

    /** Returns whether the type is assignable to the named supertype, false when it is absent. */
    default boolean isSubtype(ProcessingEnvironment processingEnv, TypeElement type, String supertype) {
        TypeMirror mirror = mirror(processingEnv, supertype);
        return mirror != null && processingEnv.getTypeUtils().isAssignable(type.asType(), mirror);
    }

    /** Reports a compile-time error anchored to the element through the messager. */
    default void error(ProcessingEnvironment processingEnv, Element element, String message) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element);
    }
}
