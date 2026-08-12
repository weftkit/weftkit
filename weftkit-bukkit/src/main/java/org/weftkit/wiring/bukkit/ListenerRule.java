package org.weftkit.wiring.bukkit;

import com.google.auto.service.AutoService;
import java.util.List;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import org.weftkit.wiring.processor.spi.ComponentRule;

/**
 * Validates {@code Listener} components at compile time: at least one {@code @EventHandler}
 * method, each public, with exactly one Bukkit event parameter.
 */
@AutoService(ComponentRule.class)
public class ListenerRule implements ComponentRule {

    private static final String LISTENER = "org.bukkit.event.Listener";

    private static final String EVENT = "org.bukkit.event.Event";

    private static final String EVENT_HANDLER = "org.bukkit.event.EventHandler";

    @Override
    public void validate(TypeElement component, ProcessingEnvironment processingEnv) {
        if (!isSubtype(processingEnv, component, LISTENER)) return;
        List<ExecutableElement> handlers = ElementFilter.methodsIn(component.getEnclosedElements()).stream()
                .filter(this::isEventHandler)
                .toList();
        if (handlers.isEmpty()) error(processingEnv, component, "Listeners need at least one @EventHandler method");
        TypeMirror event = mirror(processingEnv, EVENT);
        for (ExecutableElement handler : handlers) {
            if (!handler.getModifiers().contains(Modifier.PUBLIC))
                error(processingEnv, handler, "Event handlers must be public");
            if (handler.getParameters().size() != 1) {
                error(processingEnv, handler, "Event handlers take exactly one event parameter");
                continue;
            }
            TypeMirror parameter = handler.getParameters().get(0).asType();
            if (event != null && !processingEnv.getTypeUtils().isAssignable(parameter, event))
                error(processingEnv, handler, "Event handler parameter must be a Bukkit event");
        }
    }

    private boolean isEventHandler(ExecutableElement method) {
        return method.getAnnotationMirrors().stream()
                .map(annotation -> (TypeElement) annotation.getAnnotationType().asElement())
                .anyMatch(annotation -> annotation.getQualifiedName().contentEquals(EVENT_HANDLER));
    }
}
