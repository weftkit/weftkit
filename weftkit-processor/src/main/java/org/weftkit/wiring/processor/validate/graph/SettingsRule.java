package org.weftkit.wiring.processor.validate.graph;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import org.weftkit.wiring.ModuleSettings;
import org.weftkit.wiring.processor.compiler.Diagnostics;
import org.weftkit.wiring.processor.compiler.Mirrors;
import org.weftkit.wiring.processor.model.Component;
import org.weftkit.wiring.processor.model.Parameter;
import org.weftkit.wiring.processor.model.WiringModel;

/**
 * Backs every unbound {@code @ModuleSettings} dependency with a generated all-defaults
 * implementation, so a module always injects a real settings instance. Runs after
 * {@link DependencyRule}, which binds a single wired implementation first.
 */
final class SettingsRule extends GraphRule {

    private final Supplier<String> registryPackage;

    SettingsRule(WiringModel model, Mirrors mirrors, Diagnostics diagnostics, Supplier<String> registryPackage) {
        super(model, mirrors, diagnostics);
        this.registryPackage = registryPackage;
    }

    @Override
    void validate() {
        if (registryPackage.get() == null) return;
        // Copied because synthesizing adds components while iterating
        for (Map.Entry<String, Component> component :
                List.copyOf(model.components().entrySet()))
            for (Parameter parameter : component.getValue().parameters()) resolve(component.getKey(), parameter);
    }

    private void resolve(String component, Parameter parameter) {
        String settings = parameter.erasedClass();
        if (!parameter.qualifier().isEmpty()
                || model.isComponent(settings)
                || model.isProduct(settings, "")
                || bound(settings)) return;
        TypeElement element = mirrors.typeElement(settings);
        if (element == null
                || element.getAnnotation(ModuleSettings.class) == null
                || element.getKind() != ElementKind.INTERFACE) return;
        // A single implementation would already be bound, several left the dependency unbound
        if (implemented(element)) {
            diagnostics.error(
                    element, "@ModuleSettings interface " + settings + " has several wired implementations, keep one");
            return;
        }
        List<String> abstractMethods = abstractMethods(element);
        if (!abstractMethods.isEmpty()) {
            diagnostics.error(
                    element,
                    "@ModuleSettings interface " + settings + " has non-default methods ("
                            + String.join(", ", abstractMethods) + "), wire an implementation for " + component);
            return;
        }
        model.addBinding(settings, "", defaults(settings, element));
    }

    private String defaults(String settings, TypeElement element) {
        String existing = model.settingsDefaults().get(settings);
        if (existing != null) return existing;
        // Suffixed on a simple name clash between settings interfaces of different packages
        String base = registryPackage.get() + ".Weft" + element.getSimpleName() + "Defaults";
        String name = base;
        for (int suffix = 2; model.isComponent(name); suffix++) name = base + suffix;
        model.addComponent(name, new Component(false, false, "", List.of()));
        model.addSettingsDefault(settings, name);
        return name;
    }

    private boolean implemented(TypeElement settings) {
        for (String component : model.components().keySet()) {
            TypeElement element = mirrors.typeElement(component);
            if (element != null && mirrors.assignable(element.asType(), settings.asType())) return true;
        }
        return false;
    }

    private boolean bound(String type) {
        Map<String, String> qualified = model.bindings().get(type);
        return qualified != null && qualified.get("") != null;
    }

    private List<String> abstractMethods(TypeElement element) {
        List<String> methods = new ArrayList<>();
        for (ExecutableElement method : mirrors.allMethods(element))
            if (method.getModifiers().contains(Modifier.ABSTRACT)) methods.add(method.getSimpleName() + "()");
        return methods;
    }
}
