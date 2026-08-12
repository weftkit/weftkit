package org.weftkit.wiring.processor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;

// Whole-graph checks that run once after every element has been validated and collected
final class GraphValidator {

    private final WiringModel model;

    private final Mirrors mirrors;

    private final Diagnostics diagnostics;

    GraphValidator(WiringModel model, Mirrors mirrors, Diagnostics diagnostics) {
        this.model = model;
        this.mirrors = mirrors;
        this.diagnostics = diagnostics;
    }

    // Only external types can arrive as explicit arguments, so anything from this build must be resolvable
    void validate() {
        for (Map.Entry<String, WiringModel.Component> component :
                model.components().entrySet()) {
            TypeElement element = mirrors.typeElement(component.getKey());
            List<? extends VariableElement> constructorParameters = constructorParameters(element);
            for (WiringModel.Parameter parameter : component.getValue().parameters()) {
                Element anchor = anchor(element, constructorParameters, parameter.index());
                if (model.isComponent(parameter.erasedClass())) {
                    if (!parameter.qualifier().isEmpty())
                        diagnostics.error(
                                anchor,
                                "Qualified dependency cannot target a concrete component: " + parameter.erasedClass());
                    continue;
                }
                if (parameter.singleton())
                    diagnostics.error(anchor, "Singleton dependency is not @Wired: " + parameter.erasedClass());
                else if (model.isProduct(parameter.erasedClass(), parameter.qualifier())) continue;
                else if (parameter.internal()) bind(parameter, anchor);
                else bindExternal(parameter, anchor);
            }
        }
        model.products().forEach((type, qualified) -> {
            if (model.isComponent(type))
                qualified
                        .values()
                        .forEach(product -> diagnostics.error(
                                getterAnchor(product), "Product type is also a @Wired component: " + type));
        });
        for (Map.Entry<String, String> initializer : model.initializers().entrySet()) {
            String holder = initializer.getKey();
            Element anchor = mirrors.typeElement(initializer.getValue());
            if (model.isComponent(holder))
                diagnostics.error(anchor, "Initialized holder is also a @Wired component: " + holder);
            else if (model.isProductType(holder))
                diagnostics.error(anchor, "Initialized holder is also a @Provides product: " + holder);
        }
        for (Map.Entry<String, List<String>> requirement : model.requirements().entrySet()) {
            Element anchor = mirrors.typeElement(requirement.getKey());
            for (String holder : requirement.getValue()) {
                if (!model.isInitialized(holder)) diagnostics.error(anchor, "No @Wired loader initializes: " + holder);
            }
        }
        Set<String> cleared = new HashSet<>();
        for (String component : model.components().keySet()) checkCycle(component, new ArrayList<>(), cleared);
    }

    // cleared holds nodes whose whole subgraph is proven acyclic, so shared subgraphs are walked once.
    // A cycle member can never be cleared without its back edge first being caught by the path check
    private void checkCycle(String component, List<String> path, Set<String> cleared) {
        if (cleared.contains(component)) return;
        if (path.contains(component)) {
            // Report only the cycle itself, not the acyclic tail the search walked to reach it
            List<String> cycle = path.subList(path.indexOf(component), path.size());
            diagnostics.error(
                    mirrors.typeElement(component),
                    "Component dependency cycle: " + String.join(" -> ", cycle) + " -> " + component);
            return;
        }
        path.add(component);
        for (String dependency : model.dependencies(component)) checkCycle(dependency, path, cleared);
        path.remove(path.size() - 1);
        cleared.add(component);
    }

    private void bind(WiringModel.Parameter parameter, Element anchor) {
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
    private void bindExternal(WiringModel.Parameter parameter, Element anchor) {
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
        for (Map.Entry<String, WiringModel.Component> component :
                model.components().entrySet()) {
            if (!qualifier.isEmpty() && !qualifier.equals(component.getValue().qualifier())) continue;
            TypeElement element = mirrors.typeElement(component.getKey());
            if (element != null && mirrors.assignable(element.asType(), target.asType()))
                implementations.add(component.getKey());
        }
        return implementations;
    }

    private List<? extends VariableElement> constructorParameters(TypeElement component) {
        List<ExecutableElement> constructors = mirrors.publicConstructors(component);
        return constructors.size() == 1 ? constructors.get(0).getParameters() : List.of();
    }

    private Element anchor(TypeElement component, List<? extends VariableElement> parameters, int index) {
        return index >= 0 && index < parameters.size() ? parameters.get(index) : component;
    }

    private Element getterAnchor(WiringModel.Product product) {
        TypeElement owner = mirrors.typeElement(product.owner());
        return ElementFilter.methodsIn(owner.getEnclosedElements()).stream()
                .filter(method -> method.getSimpleName().contentEquals(product.getter()))
                .findFirst()
                .map(Element.class::cast)
                .orElse(owner);
    }
}
