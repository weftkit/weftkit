package org.weftkit.wiring.processor.validate.graph;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import org.weftkit.wiring.processor.compiler.Diagnostics;
import org.weftkit.wiring.processor.compiler.Mirrors;
import org.weftkit.wiring.processor.model.Component;
import org.weftkit.wiring.processor.model.Parameter;
import org.weftkit.wiring.processor.model.WiringModel;

// Ambient declarations must not shadow wired components, and a component that leans on explicit
// create arguments cannot be injected, since arguments never travel into resolved dependencies
final class AmbientRule extends GraphRule {

    private final Supplier<String> registryClass;

    AmbientRule(WiringModel model, Mirrors mirrors, Diagnostics diagnostics, Supplier<String> registryClass) {
        super(model, mirrors, diagnostics);
        this.registryClass = registryClass;
    }

    @Override
    void validate() {
        for (String declared : model.ambientTypes())
            if (model.isComponent(declared))
                error(
                        registryAnchor(),
                        "Ambient declaration shadows the @Wired component " + declared + ", drop one of the two");
        model.components().forEach(this::validateInjections);
    }

    private void validateInjections(String component, Component definition) {
        // Generated settings defaults have no source element and no parameters to check
        if (definition.parameters().isEmpty()) return;
        TypeElement element = mirrors.typeElement(component);
        if (element == null) return;
        List<? extends VariableElement> constructorParameters = constructorParameters(element);
        for (Parameter parameter : definition.parameters()) {
            String target = target(parameter);
            if (target == null || !model.isArgumentDependent(target)) continue;
            error(
                    anchor(element, constructorParameters, parameter.index()),
                    "Dependency " + target + " needs explicit create arguments and cannot be injected."
                            + " Build it with create(" + simpleName(target) + ".class, ...) instead");
        }
    }

    private String target(Parameter parameter) {
        if (parameter.qualifier().isEmpty() && model.isComponent(parameter.erasedClass()))
            return parameter.erasedClass();
        Map<String, String> qualified = model.bindings().get(parameter.erasedClass());
        return qualified == null ? null : qualified.get(parameter.qualifier());
    }

    private static String simpleName(String type) {
        return type.substring(type.lastIndexOf('.') + 1);
    }

    private Element registryAnchor() {
        String registry = registryClass.get();
        return registry == null ? null : mirrors.typeElement(registry);
    }

    private void error(Element anchor, String message) {
        if (anchor == null) diagnostics.error(message);
        else diagnostics.error(anchor, message);
    }
}
