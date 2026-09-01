package org.weftkit.wiring.processor.validate.graph;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import org.weftkit.wiring.FeatureFlags;
import org.weftkit.wiring.ModuleSettings;
import org.weftkit.wiring.processor.compiler.Diagnostics;
import org.weftkit.wiring.processor.compiler.Mirrors;
import org.weftkit.wiring.processor.model.Component;
import org.weftkit.wiring.processor.model.Parameter;
import org.weftkit.wiring.processor.model.WiringModel;

// Resolves every constructor parameter against the graph and records the interface bindings the
// generators read. Ambient resolution is declaration based, so a singleton parameter no layer
// covers fails the build here instead of the server. Only plain components may lean on explicit
// create arguments
final class DependencyRule extends GraphRule {

    private static final String WEFT_LOADER = "org.weftkit.wiring.runtime.WeftLoader";

    private static final String WEFT = "org.weftkit.wiring.runtime.Weft";

    private static final String WEFT_VIEW = "org.weftkit.wiring.runtime.WeftView";

    private static final String FEATURE_FLAGS = FeatureFlags.class.getCanonicalName();

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
            validateParameter(
                    component, definition, parameter, anchor(element, constructorParameters, parameter.index()));
    }

    private void validateParameter(String component, Component definition, Parameter parameter, Element anchor) {
        if (parameter.erasedClass().equals(WEFT_LOADER)) {
            diagnostics.error(anchor, "WeftLoader is not injectable, inject Weft to enumerate components by supertype");
            return;
        }
        // The facades resolve through their own layer
        if (parameter.erasedClass().equals(WEFT) || parameter.erasedClass().equals(WEFT_VIEW)) return;
        // The ambient layer outranks components and products, so ambiguous declarations are
        // rejected for every parameter, whichever layer resolves it
        List<String> covering = ambientCoverage(parameter);
        if (covering.size() > 1 && !chain(covering)) {
            diagnostics.error(
                    anchor,
                    "Ambient declarations are ambiguous for " + parameter.erasedClass() + ": "
                            + String.join(", ", covering) + " are all assignable and unrelated");
            return;
        }
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
        if (parameter.internal()) bind(definition, parameter, anchor);
        else bindExternal(component, definition, parameter, covering, anchor);
    }

    private void bind(Component definition, Parameter parameter, Element anchor) {
        List<String> implementations = implementations(parameter.erasedClass(), parameter.qualifier());
        if (implementations.size() == 1) {
            model.addBinding(parameter.erasedClass(), parameter.qualifier(), implementations.get(0));
            return;
        }
        if (implementations.size() > 1) {
            diagnostics.error(
                    anchor,
                    "Ambiguous dependency " + parameter.erasedClass() + ", implemented by "
                            + String.join(", ", implementations));
            return;
        }
        if (moduleSettings(parameter)) return;
        if (!parameter.optional())
            diagnostics.error(anchor, "Dependency must be @Wired or a @Provides product: " + parameter.erasedClass());
        else warnNeverPresent(definition, parameter, anchor);
    }

    // An unimplemented @ModuleSettings dependency is backed by a generated default in SettingsRule
    private boolean moduleSettings(Parameter parameter) {
        TypeElement element = mirrors.typeElement(parameter.erasedClass());
        return element != null && element.getAnnotation(ModuleSettings.class) != null;
    }

    // External types resolve through the declared ambient types or a wired implementation. With
    // neither, a singleton parameter is a guaranteed server failure and rejected here, while a
    // plain component may receive the value as an explicit create argument
    private void bindExternal(
            String component, Component definition, Parameter parameter, List<String> covering, Element anchor) {
        List<String> implementations = implementations(parameter.erasedClass(), parameter.qualifier());
        // A single implementation binds even under ambient coverage: the ambient layer wins when
        // a value is present, the binding backs an absent one
        if (implementations.size() == 1) {
            model.addBinding(parameter.erasedClass(), parameter.qualifier(), implementations.get(0));
            return;
        }
        // The ambient layer resolves it. Related declared types share one value at runtime
        if (!covering.isEmpty()) {
            model.addAmbientEdge(component, parameter.erasedClass());
            return;
        }
        if (moduleSettings(parameter)) return;
        if (implementations.size() > 1) {
            if (definition.singleton())
                diagnostics.error(
                        anchor,
                        "Ambiguous dependency " + parameter.erasedClass() + ", implemented by "
                                + String.join(", ", implementations) + ". Add @Qualified or declare an ambient type");
            else
                diagnostics.warning(
                        anchor,
                        "External dependency " + parameter.erasedClass() + " has multiple @Wired implementations ("
                                + String.join(", ", implementations)
                                + ") and is left unbound. Supply one as a create argument");
            return;
        }
        if (parameter.optional()) {
            warnNeverPresent(definition, parameter, anchor);
            return;
        }
        if (definition.singleton())
            diagnostics.error(
                    anchor,
                    "Dependency of a singleton must be wired, a product, or a declared ambient type: "
                            + parameter.erasedClass() + ". Declare it with @Registry(ambient) or a registry"
                            + " constructor parameter");
        else model.markArgumentDependent(component);
    }

    // Arguments never reach singletons, so nothing can ever fill the optional
    private void warnNeverPresent(Component definition, Parameter parameter, Element anchor) {
        if (!definition.singleton()) return;
        diagnostics.warning(
                anchor,
                "Optional dependency " + parameter.erasedClass()
                        + " of a singleton can never be present. Declare an ambient type or drop the parameter");
    }

    // Declared ambient types assignable to the parameter. Ambient values carry no qualifier, so a
    // qualified parameter is never covered
    private List<String> ambientCoverage(Parameter parameter) {
        if (!parameter.qualifier().isEmpty()) return List.of();
        TypeMirror target = mirrors.mirror(parameter.erasedClass());
        if (target == null) return List.of();
        // The loader declares FeatureFlags implicitly
        Set<String> declared = new LinkedHashSet<>(model.ambientTypes());
        declared.add(FEATURE_FLAGS);
        List<String> covering = new ArrayList<>();
        for (String name : declared) {
            TypeMirror mirror = mirrors.mirror(name);
            if (mirror != null && mirrors.assignable(mirror, target)) covering.add(name);
        }
        return covering;
    }

    // Subtype-related declarations collapse onto one value or fail loader construction, only
    // unrelated ones are truly ambiguous
    private boolean chain(List<String> covering) {
        for (int first = 0; first < covering.size(); first++)
            for (int second = first + 1; second < covering.size(); second++) {
                TypeMirror one = mirrors.mirror(covering.get(first));
                TypeMirror other = mirrors.mirror(covering.get(second));
                if (!mirrors.assignable(one, other) && !mirrors.assignable(other, one)) return false;
            }
        return true;
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
}
