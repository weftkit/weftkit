package org.weftkit.wiring.paper.processor;

import static org.weftkit.wiring.processor.spi.AnnotationMirrors.annotation;
import static org.weftkit.wiring.processor.spi.AnnotationMirrors.stringValue;
import static org.weftkit.wiring.processor.spi.AnnotationMirrors.stringValues;

import com.google.auto.service.AutoService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.TypeElement;
import org.weftkit.wiring.FeatureFlag;
import org.weftkit.wiring.Singleton;
import org.weftkit.wiring.processor.spi.ComponentRule;

/**
 * Validates {@code @PaperCommand} components at compile time: a {@code @Singleton} implementing
 * exactly one of {@code BasicCommand} or {@code CommandTree}, a typeable label, distinct typeable
 * aliases that differ from the label, no label or alias shared with another command of the same
 * compilation unless both carry different feature flags, and no {@code @CommandHandler} on the same
 * component.
 */
@AutoService(ComponentRule.class)
public final class PaperCommandRule implements ComponentRule {

    private static final String PAPER_COMMAND = "org.weftkit.wiring.paper.commands.PaperCommand";

    private static final String COMMAND_HANDLER = "org.weftkit.wiring.bukkit.commands.CommandHandler";

    private static final String BASIC_COMMAND = "io.papermc.paper.command.brigadier.BasicCommand";

    private static final String COMMAND_TREE = "org.weftkit.wiring.paper.commands.CommandTree";

    private final Map<String, List<TypeElement>> owners = new HashMap<>();

    @Override
    public void validate(TypeElement component, ProcessingEnvironment processingEnv) {
        AnnotationMirror command = annotation(component, PAPER_COMMAND);
        if (command == null) return;
        Consumer<String> report = message -> error(processingEnv, component, message);

        if (isSubtype(processingEnv, component, BASIC_COMMAND) == isSubtype(processingEnv, component, COMMAND_TREE))
            report.accept("@PaperCommand components must implement exactly one of BasicCommand or CommandTree");
        if (component.getAnnotation(Singleton.class) == null)
            report.accept("@PaperCommand components must be a @Singleton");
        if (annotation(component, COMMAND_HANDLER) != null)
            report.accept("Use either @PaperCommand or @CommandHandler, not both");

        String label = stringValue(command, "value");
        checkLabel("label", label, report);
        Set<String> names = new LinkedHashSet<>(Set.of(label));
        for (String alias : stringValues(command, "aliases")) {
            checkLabel("aliases", alias, report);
            if (!alias.isBlank() && !names.add(alias))
                report.accept("@PaperCommand aliases must be distinct and differ from the label");
        }
        for (String name : names) {
            if (name.isBlank()) continue;
            List<TypeElement> claimants = owners.computeIfAbsent(name, key -> new ArrayList<>());
            if (claimants.stream().anyMatch(owner -> isSame(owner, component))) continue;
            claimants.stream()
                    .filter(owner -> canBothBeEnabled(owner, component))
                    .findFirst()
                    .ifPresent(owner -> report.accept(
                            "@PaperCommand label \"" + name + "\" is already taken by " + owner.getQualifiedName()));
            claimants.add(component);
        }
    }

    private static boolean isSame(TypeElement owner, TypeElement component) {
        return owner.getQualifiedName().contentEquals(component.getQualifiedName());
    }

    private static boolean canBothBeEnabled(TypeElement owner, TypeElement component) {
        FeatureFlag ownerFlag = owner.getAnnotation(FeatureFlag.class);
        FeatureFlag flag = component.getAnnotation(FeatureFlag.class);
        return ownerFlag == null || flag == null || ownerFlag.value().equals(flag.value());
    }

    private static void checkLabel(String what, String label, Consumer<String> report) {
        if (label.isBlank()) report.accept("@PaperCommand " + what + " must not be blank");
        else if (label.chars().anyMatch(c -> Character.isWhitespace(c) || c == ':'))
            report.accept("@PaperCommand " + what + " must not contain whitespace or ':'");
        else if (label.startsWith("/")) report.accept("@PaperCommand " + what + " must not start with '/'");
        else if (label.chars().anyMatch(Character::isUpperCase))
            report.accept("@PaperCommand " + what + " must be lowercase");
    }
}
