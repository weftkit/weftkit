package org.weftkit.wiring.processor.validate.element;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import org.weftkit.wiring.Initializes;
import org.weftkit.wiring.Loader;
import org.weftkit.wiring.Requires;
import org.weftkit.wiring.processor.compiler.Diagnostics;
import org.weftkit.wiring.processor.compiler.Mirrors;
import org.weftkit.wiring.processor.model.ModelCollector;
import org.weftkit.wiring.processor.spi.ComponentRule;

// A @Wired class must be constructible by the generated wiring, which also gates the SPI rules,
// the static holder scan, and collection into the model
public final class WiredRule extends ElementRule<TypeElement> {

    private static final String LOADER = Loader.class.getCanonicalName();

    private final ProcessingEnvironment processingEnv;

    private final List<ComponentRule> rules = loadRules();

    private final StaticHolderCheck holderCheck;

    public WiredRule(
            ProcessingEnvironment processingEnv, Mirrors mirrors, Diagnostics diagnostics, ModelCollector collector) {
        super(mirrors, diagnostics, collector);
        this.processingEnv = processingEnv;
        this.holderCheck = new StaticHolderCheck(processingEnv);
    }

    @Override
    public void validate(TypeElement component) {
        List<ExecutableElement> constructors = mirrors.accessibleConstructors(component);
        boolean loader = mirrors.isSubtype(component, LOADER);
        boolean valid = diagnostics
                .check(component)
                .require(
                        component.getKind() == ElementKind.CLASS
                                && !component.getModifiers().contains(Modifier.ABSTRACT),
                        "@Wired requires a concrete class")
                // Type parameters erase to their bounds, so resolution could match arbitrary arguments
                .require(component.getTypeParameters().isEmpty(), "@Wired classes cannot be generic")
                .require(
                        mirrors.isInstantiable(component),
                        "@Wired classes must be top-level or static nested and at least package visible")
                .require(constructors.size() == 1, "@Wired components need exactly one accessible constructor")
                // A per-injection component would never have its load() called
                .require(!loader || mirrors.isSingleton(component), "Loader implementations must be @Singleton")
                .passed();
        if (!valid) return;
        for (ComponentRule rule : rules) rule.validate(component, processingEnv);
        holderCheck.check(component, constructors.get(0), loader, declaredHolders(component));
        collector.collect(component, constructors.get(0));
    }

    private Set<String> declaredHolders(TypeElement component) {
        Set<String> declared = new HashSet<>();
        Requires requires = component.getAnnotation(Requires.class);
        if (requires != null) declared.addAll(mirrors.holders(requires::value));
        Initializes initializes = component.getAnnotation(Initializes.class);
        if (initializes != null) declared.addAll(mirrors.holders(initializes::value));
        return declared;
    }

    private static List<ComponentRule> loadRules() {
        List<ComponentRule> rules = new ArrayList<>();
        for (ComponentRule rule : ServiceLoader.load(ComponentRule.class, ComponentRule.class.getClassLoader()))
            rules.add(rule);
        return rules;
    }
}
