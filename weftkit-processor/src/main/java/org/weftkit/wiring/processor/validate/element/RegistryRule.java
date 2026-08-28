package org.weftkit.wiring.processor.validate.element;

import java.util.List;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import org.weftkit.wiring.Registry;
import org.weftkit.wiring.processor.compiler.Diagnostics;
import org.weftkit.wiring.processor.compiler.Mirrors;
import org.weftkit.wiring.processor.model.ModelCollector;

// Exactly one @Registry class anchors the build and provides the ambient constructor values
public final class RegistryRule extends ElementRule<TypeElement> {

    private final ModuleRule modules;

    public RegistryRule(Mirrors mirrors, Diagnostics diagnostics, ModelCollector collector, ModuleRule modules) {
        super(mirrors, diagnostics, collector);
        this.modules = modules;
    }

    @Override
    public void validate(TypeElement element) {
        String name = element.getQualifiedName().toString();
        String registry = collector.registryClass();
        List<ExecutableElement> constructors = mirrors.publicConstructors(element);
        boolean valid = diagnostics
                .check(element)
                .require(registry == null || registry.equals(name), "@Registry is already declared on " + registry)
                .require(constructors.size() == 1, "@Registry classes need exactly one public constructor")
                .passed();
        if (!valid) return;
        collector.registerRegistry(element, constructors.get(0));
        Registry annotation = element.getAnnotation(Registry.class);
        modules.declare(element, mirrors.holders(annotation::modules), annotation.disable());
    }
}
