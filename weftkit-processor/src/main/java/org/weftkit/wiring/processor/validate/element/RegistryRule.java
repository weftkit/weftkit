package org.weftkit.wiring.processor.validate.element;

import java.util.ArrayList;
import java.util.List;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import org.weftkit.wiring.Registry;
import org.weftkit.wiring.processor.compiler.Diagnostics;
import org.weftkit.wiring.processor.compiler.Mirrors;
import org.weftkit.wiring.processor.model.ModelCollector;

// Exactly one @Registry class anchors the build and declares the ambient types: itself, its
// constructor parameters, and the ambient attribute
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
        Registry annotation = element.getAnnotation(Registry.class);
        collector.registerRegistry(element, constructors.get(0), ambientAttribute(element, annotation));
        modules.declare(element, mirrors.holders(annotation::modules), annotation.disable());
    }

    // The entries end up as class literals in the generated registry, so each must be a
    // reference type the registry package can see
    private List<TypeMirror> ambientAttribute(TypeElement element, Registry annotation) {
        String registryPackage = mirrors.packageName(element);
        List<TypeMirror> declared = new ArrayList<>();
        for (TypeMirror type : mirrors.classValues(annotation::ambient)) {
            boolean valid = diagnostics
                    .check(element)
                    .require(
                            type instanceof DeclaredType,
                            "@Registry(ambient) entries must be classes or interfaces: " + type)
                    .require(
                            !(type instanceof DeclaredType) || mirrors.isAccessibleFrom(type, registryPackage),
                            "@Registry(ambient) entries must be public or live in the registry package: " + type)
                    .passed();
            if (valid) declared.add(type);
        }
        return declared;
    }
}
