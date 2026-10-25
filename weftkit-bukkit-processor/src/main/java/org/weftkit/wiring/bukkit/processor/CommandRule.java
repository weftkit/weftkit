package org.weftkit.wiring.bukkit.processor;

import static org.weftkit.wiring.processor.spi.AnnotationMirrors.annotation;
import static org.weftkit.wiring.processor.spi.AnnotationMirrors.stringValue;

import com.google.auto.service.AutoService;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
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
        AnnotationMirror handler = annotation(component, COMMAND_HANDLER);
        if (handler == null) return;
        if (!isSubtype(processingEnv, component, COMMAND_EXECUTOR))
            error(processingEnv, component, "@CommandHandler components must implement CommandExecutor");
        if (stringValue(handler, "value").isBlank())
            error(processingEnv, component, "@CommandHandler needs a command name");
    }
}
