package org.weftkit.wiring.bukkit.processor;

import com.google.auto.service.AutoService;
import java.util.Map;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import org.weftkit.wiring.processor.spi.ComponentRule;

/**
 * Validates {@code @CommandHandler} components at compile time: the component implements
 * {@code CommandExecutor} and names a non-blank command.
 */
@AutoService(ComponentRule.class)
public final class CommandRule implements ComponentRule {

    private static final String COMMAND_HANDLER = "org.weftkit.wiring.bukkit.commands.CommandHandler";

    private static final String COMMAND_EXECUTOR = "org.bukkit.command.CommandExecutor";

    @Override
    public void validate(TypeElement component, ProcessingEnvironment processingEnv) {
        AnnotationMirror handler = annotation(component);
        if (handler == null) return;
        if (!isSubtype(processingEnv, component, COMMAND_EXECUTOR))
            error(processingEnv, component, "@CommandHandler components must implement CommandExecutor");
        if (name(handler).isBlank()) error(processingEnv, component, "@CommandHandler needs a command name");
    }

    private AnnotationMirror annotation(TypeElement component) {
        return component.getAnnotationMirrors().stream()
                .filter(mirror -> ((TypeElement) mirror.getAnnotationType().asElement())
                        .getQualifiedName()
                        .contentEquals(COMMAND_HANDLER))
                .findFirst()
                .orElse(null);
    }

    private String name(AnnotationMirror handler) {
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
                handler.getElementValues().entrySet())
            if (entry.getKey().getSimpleName().contentEquals("value"))
                return String.valueOf(entry.getValue().getValue());
        return "";
    }
}
