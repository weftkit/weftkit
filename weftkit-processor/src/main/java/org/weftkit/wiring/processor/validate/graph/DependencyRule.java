package org.weftkit.wiring.processor.validate.graph;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import org.weftkit.wiring.processor.compiler.Diagnostics;
import org.weftkit.wiring.processor.compiler.Mirrors;
import org.weftkit.wiring.processor.model.Component;
import org.weftkit.wiring.processor.model.Parameter;
import org.weftkit.wiring.processor.model.WiringModel;

// Resolves every constructor parameter against the graph and records the interface bindings the
// generators read. Only external types can arrive as explicit arguments, so anything declared in
// this build must resolve here
final class DependencyRule extends GraphRule {

    DependencyRule(WiringModel model, Mirrors mirrors, Diagnostics diagnostics) {
        super(model, mirrors, diagnostics);
    }

    @Override
    void validate() {
        model.components().forEach(this::validateComponent);
    }

    private void validateComponent(String component, Component definition) {
        TypeElement element = mirrors.typeElement(component);
        List<? extends VariableElement> constructorParameters = constructorParameters(element);
        for (Parameter parameter : definition.parameters())
            validateParameter(parameter, anchor(element, constructorParameters, parameter.index()));
    }

    private void validateParameter(Parameter parameter, Element anchor) {
        if (model.isComponent(parameter.erasedClass())) {
            if (!parameter.qualifier().isEmpty())
                diagnostics.error(
                        anchor, "Qualified dependency cannot target a concrete component: " + parameter.erasedClass());
            return;
        }
        if (parameter.singleton()) {
            diagnostics.error(anchor, "Singleton dependency is not @Wired: " + parameter.erasedClass());
            return;
        }
        if (model.isProduct(parameter.erasedClass(), parameter.qualifier())) return;
        if (parameter.internal()) bind(parameter, anchor);
        else bindExternal(parameter, anchor);
    }

    private void bind(Parameter parameter, Element anchor) {
        List<String> implementations = implementations(parameter.erasedClass(), parameter.qualifier());
        if (implementations.size() == 1)
            model.addBinding(parameter.erasedClass(), parameter.qualifier(), implementations.get(0));
        else if (implementations.size() > 1)
            diagnostics.error(
                    anchor,
                    "Ambiguous dependency " + parameter.erasedClass() + ", implemented by "
                            + String.join(", ", implementations));
        else if (!parameter.optional())
            diagnostics.error(anchor, "Dependency must be @Wired or a @Provides product: " + parameter.erasedClass());
    }

    // External abstractions may arrive as explicit arguments, so bind only when a single
    // implementation makes the choice unambiguous. Zero implementations is the normal ambient case
    // and stays silent. Several is a latent runtime failure worth flagging without forcing a choice
    private void bindExternal(Parameter parameter, Element anchor) {
        List<String> implementations = implementations(parameter.erasedClass(), parameter.qualifier());
        if (implementations.size() == 1)
            model.addBinding(parameter.erasedClass(), parameter.qualifier(), implementations.get(0));
        else if (implementations.size() > 1)
            diagnostics.warning(
                    anchor,
                    "External dependency " + parameter.erasedClass() + " has multiple @Wired implementations ("
                            + String.join(", ", implementations)
                            + ") and is left unbound; supply one as a @Registry argument or ambient value at runtime");
    }

    private List<String> implementations(String abstraction, String qualifier) {
        TypeElement target = mirrors.typeElement(abstraction);
        if (target == null
                || (target.getKind() != ElementKind.INTERFACE
                        && !target.getModifiers().contains(Modifier.ABSTRACT))) return List.of();
        List<String> implementations = new ArrayList<>();
        for (Map.Entry<String, Component> component : model.components().entrySet()) {
            if (!qualifier.isEmpty() && !qualifier.equals(component.getValue().qualifier())) continue;
            TypeElement element = mirrors.typeElement(component.getKey());
            if (element != null && mirrors.assignable(element.asType(), target.asType()))
                implementations.add(component.getKey());
        }
        return implementations;
    }

    private List<? extends VariableElement> constructorParameters(TypeElement component) {
        List<ExecutableElement> constructors = mirrors.accessibleConstructors(component);
        return constructors.size() == 1 ? constructors.get(0).getParameters() : List.of();
    }

    private Element anchor(TypeElement component, List<? extends VariableElement> parameters, int index) {
        return index >= 0 && index < parameters.size() ? parameters.get(index) : component;
    }
}
