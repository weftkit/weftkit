package org.weftkit.wiring.processor.spi;

import java.util.List;
import java.util.Map;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.SimpleAnnotationValueVisitor14;

/**
 * Annotation lookups for {@link ComponentRule}s that match annotations by name. An unresolved
 * string reads as {@code "<error>"}, javac reports it itself.
 */
public final class AnnotationMirrors {

    private static final SimpleAnnotationValueVisitor14<String, Void> STRING =
            new SimpleAnnotationValueVisitor14<>("") {
                @Override
                public String visitString(String value, Void unused) {
                    return value;
                }
            };

    private static final SimpleAnnotationValueVisitor14<List<String>, Void> STRINGS =
            new SimpleAnnotationValueVisitor14<>(List.of()) {
                @Override
                public List<String> visitArray(List<? extends AnnotationValue> values, Void unused) {
                    return values.stream()
                            .map(value -> value.accept(STRING, null))
                            .toList();
                }
            };

    private AnnotationMirrors() {}

    /** Returns the element's annotation of the named type, or null when it is absent. */
    public static AnnotationMirror annotation(Element element, String qualifiedName) {
        return element.getAnnotationMirrors().stream()
                .filter(mirror -> ((TypeElement) mirror.getAnnotationType().asElement())
                        .getQualifiedName()
                        .contentEquals(qualifiedName))
                .findFirst()
                .orElse(null);
    }

    /** Returns the explicitly set value of the named annotation element, or null when it is defaulted. */
    public static AnnotationValue annotationValue(AnnotationMirror annotation, String name) {
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
                annotation.getElementValues().entrySet())
            if (entry.getKey().getSimpleName().contentEquals(name)) return entry.getValue();
        return null;
    }

    /** Returns the explicitly set string value of the named annotation element, empty when it is defaulted. */
    public static String stringValue(AnnotationMirror annotation, String name) {
        AnnotationValue value = annotationValue(annotation, name);
        return value == null ? "" : value.accept(STRING, null);
    }

    /** Returns the explicitly set string array of the named annotation element, empty when it is defaulted. */
    public static List<String> stringValues(AnnotationMirror annotation, String name) {
        AnnotationValue value = annotationValue(annotation, name);
        return value == null ? List.of() : value.accept(STRINGS, null);
    }
}
