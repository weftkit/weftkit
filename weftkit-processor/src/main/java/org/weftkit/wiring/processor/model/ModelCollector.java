package org.weftkit.wiring.processor.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import org.weftkit.wiring.Singleton;
import org.weftkit.wiring.processor.compiler.Diagnostics;
import org.weftkit.wiring.processor.compiler.Mirrors;

/** Builds the {@link WiringModel} from validated elements, reporting entries it cannot parse. */
public final class ModelCollector {

    private final WiringModel model;

    private final Mirrors mirrors;

    private final Diagnostics diagnostics;

    private final Set<String> sources = new HashSet<>();

    private String registry;

    private String registryPackage;

    private List<TypeMirror> ambient = List.of();

    public ModelCollector(WiringModel model, Mirrors mirrors, Diagnostics diagnostics) {
        this.model = model;
        this.mirrors = mirrors;
        this.diagnostics = diagnostics;
    }

    public void addSources(RoundEnvironment roundEnvironment) {
        for (TypeElement type : ElementFilter.typesIn(roundEnvironment.getRootElements()))
            sources.add(type.getQualifiedName().toString());
    }

    public String registryClass() {
        return registry;
    }

    public String registryPackage() {
        return registryPackage;
    }

    public void registerRegistry(TypeElement element, ExecutableElement constructor) {
        registry = element.getQualifiedName().toString();
        registryPackage = mirrors.packageName(element);
        ambient = Stream.concat(
                        Stream.of(element.asType()),
                        constructor.getParameters().stream().map(VariableElement::asType))
                .toList();
    }

    public void collect(TypeElement component, ExecutableElement constructor) {
        if (!mirrors.isPublic(component))
            model.markHidden(component.getQualifiedName().toString(), mirrors.packageName(component));
        Set<String> seen = new HashSet<>();
        String componentPackage = mirrors.packageName(component);
        List<Parameter> parameters = new ArrayList<>();
        List<? extends VariableElement> constructorParameters = constructor.getParameters();
        for (int index = 0; index < constructorParameters.size(); index++) {
            VariableElement parameter = constructorParameters.get(index);
            Parameter collected = collectParameter(parameter, index, componentPackage);
            if (collected == null) continue;
            // Resolution is type-based, so a second parameter of the same type could only receive the same value
            if (!seen.add(collected.erasedClass() + "|" + collected.qualifier()))
                diagnostics.error(parameter, "Duplicate constructor parameter type: " + collected.erasedClass());
            parameters.add(collected);
        }
        Singleton singleton = component.getAnnotation(Singleton.class);
        model.addComponent(
                component.getQualifiedName().toString(),
                new Component(
                        singleton != null,
                        singleton != null && singleton.lazy(),
                        mirrors.qualifier(component),
                        parameters));
    }

    /** Records the product and returns the previous owner of the same product, or null. */
    public Product addProduct(ExecutableElement getter, TypeElement owner) {
        String product = mirrors.erased(getter.getReturnType());
        if (!mirrors.isReferencable(getter.getReturnType()))
            model.markHidden(product, mirrors.packageName(getter.getReturnType()));
        return model.addProduct(
                product,
                mirrors.qualifier(getter),
                new Product(
                        owner.getQualifiedName().toString(),
                        getter.getSimpleName().toString()));
    }

    /** Records the initializer and returns the previous initializer of the same holder, or null. */
    public String addInitializer(String holder, TypeElement component) {
        return model.addInitializer(holder, component.getQualifiedName().toString());
    }

    public void addRequirements(TypeElement component, List<String> holders) {
        model.addRequirements(component.getQualifiedName().toString(), holders);
    }

    private Parameter collectParameter(VariableElement parameter, int index, String componentPackage) {
        TypeMirror type = parameter.asType();
        boolean optional = mirrors.isOptional(type);
        if (optional) {
            type = mirrors.optionalArgument(type);
            if (type == null) {
                diagnostics.error(parameter, "Optional dependencies need a concrete type argument");
                return null;
            }
        }
        String erased = mirrors.erased(type);
        if (type.getKind().isPrimitive()) {
            diagnostics.error(parameter, "@Wired constructors cannot take primitive parameters");
            return null;
        }
        if (!mirrors.isReferencable(type)) {
            if (!mirrors.isAccessibleFrom(type, componentPackage)) {
                diagnostics.error(
                        parameter,
                        "Constructor parameter types must be public or live in the component package: " + erased);
                return null;
            }
            model.markHidden(erased, mirrors.packageName(type));
        }
        return new Parameter(
                erased,
                type.toString(),
                mirrors.qualifier(parameter),
                mirrors.isSingleton(type),
                isInternal(type),
                optional,
                index);
    }

    // Internal means declared in this compilation and not already satisfied by a @Registry constructor argument.
    // On an incremental rebuild only annotated sources are guaranteed to recompile, so an unannotated in-project
    // type can be misread as external there; the unresolved-dependency error then only fires on a clean build.
    private boolean isInternal(TypeMirror type) {
        for (TypeMirror provided : ambient) if (mirrors.assignable(provided, type)) return false;
        for (Element element = mirrors.asElement(type);
                element instanceof TypeElement typeElement;
                element = element.getEnclosingElement()) {
            if (sources.contains(typeElement.getQualifiedName().toString())) return true;
        }
        return false;
    }
}
