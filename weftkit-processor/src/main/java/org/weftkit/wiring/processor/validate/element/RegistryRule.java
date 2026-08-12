package org.weftkit.wiring.processor.validate.element;

import java.util.List;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import org.weftkit.wiring.processor.compiler.Diagnostics;
import org.weftkit.wiring.processor.compiler.Mirrors;
import org.weftkit.wiring.processor.model.ModelCollector;

// Exactly one @Registry class anchors the build and provides the ambient constructor values
public final class RegistryRule extends ElementRule<TypeElement> {

    public RegistryRule(Mirrors mirrors, Diagnostics diagnostics, ModelCollector collector) {
        super(mirrors, diagnostics, collector);
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
    }
}
