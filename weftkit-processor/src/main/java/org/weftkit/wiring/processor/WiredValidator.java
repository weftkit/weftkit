package org.weftkit.wiring.processor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import org.weftkit.wiring.Initializes;
import org.weftkit.wiring.Loader;
import org.weftkit.wiring.Requires;
import org.weftkit.wiring.Singleton;
import org.weftkit.wiring.StaticHolder;
import org.weftkit.wiring.Wired;
import org.weftkit.wiring.processor.spi.ComponentRule;

class WiredValidator {

    private static final String LOADER = Loader.class.getCanonicalName();

    private final ProcessingEnvironment processingEnv;

    private final WiringModel model;

    private final Mirrors mirrors;

    private final Diagnostics diagnostics;

    private final GraphValidator graph;

    private final Set<String> sources = new HashSet<>();

    private final List<ComponentRule> rules = loadRules();

    private final StaticHolderCheck holderCheck;

    private String registry;

    private String registryPackage;

    private List<TypeMirror> ambient = List.of();

    WiredValidator(ProcessingEnvironment processingEnv, WiringModel model) {
        this.processingEnv = processingEnv;
        this.model = model;
        this.mirrors = new Mirrors(processingEnv);
        this.diagnostics = new Diagnostics(processingEnv);
        this.graph = new GraphValidator(model, mirrors, diagnostics);
        this.holderCheck = new StaticHolderCheck(processingEnv);
    }

    void addSources(RoundEnvironment roundEnvironment) {
        for (TypeElement type : ElementFilter.typesIn(roundEnvironment.getRootElements()))
            sources.add(type.getQualifiedName().toString());
    }

    String registryPackage() {
        return registryPackage;
    }

    String registryClass() {
        return registry;
    }

    void validateRegistry(TypeElement element) {
        String name = element.getQualifiedName().toString();
        if (registry != null && !registry.equals(name)) {
            diagnostics.error(element, "@Registry is already declared on " + registry);
            return;
        }
        List<ExecutableElement> constructors = mirrors.publicConstructors(element);
        if (constructors.size() != 1) {
            diagnostics.error(element, "@Registry classes need exactly one public constructor");
            return;
        }
        registry = name;
        registryPackage = mirrors.packageName(element);
        ambient = Stream.concat(
                        Stream.of(element.asType()),
                        constructors.get(0).getParameters().stream().map(VariableElement::asType))
                .toList();
    }

    void validateComponent(TypeElement component) {
        if (component.getKind() != ElementKind.CLASS || component.getModifiers().contains(Modifier.ABSTRACT)) {
            diagnostics.error(component, "@Wired requires a concrete class");
            return;
        }
        // Type parameters erase to their bounds, so resolution could match arbitrary arguments
        if (!component.getTypeParameters().isEmpty()) {
            diagnostics.error(component, "@Wired classes cannot be generic");
            return;
        }
        if (!mirrors.isInstantiable(component)) {
            diagnostics.error(component, "@Wired classes must be public and top-level or static nested");
            return;
        }
        List<ExecutableElement> constructors = mirrors.publicConstructors(component);
        if (constructors.size() != 1) {
            diagnostics.error(component, "@Wired components need exactly one public constructor");
            return;
        }
        // A per-injection component would never have its load() called
        if (mirrors.isSubtype(component, LOADER) && !mirrors.isSingleton(component)) {
            diagnostics.error(component, "Loader implementations must be @Singleton");
            return;
        }
        if (mirrors.isLazy(component) && mirrors.isSubtype(component, LOADER)) {
            diagnostics.error(component, "Lazy singletons cannot implement Loader");
            return;
        }
        for (ComponentRule rule : rules) rule.validate(component, processingEnv);
        holderCheck.check(
                component, constructors.get(0), mirrors.isSubtype(component, LOADER), declaredHolders(component));
        collect(component, constructors.get(0));
    }

    private Set<String> declaredHolders(TypeElement component) {
        Set<String> declared = new HashSet<>();
        Requires requires = component.getAnnotation(Requires.class);
        if (requires != null) declared.addAll(mirrors.holders(requires::value));
        Initializes initializes = component.getAnnotation(Initializes.class);
        if (initializes != null) declared.addAll(mirrors.holders(initializes::value));
        return declared;
    }

    void validateProduct(ExecutableElement getter) {
        TypeElement owner = (TypeElement) getter.getEnclosingElement();
        if (!getter.getModifiers().contains(Modifier.PUBLIC)
                || !getter.getParameters().isEmpty()
                || getter.getReturnType().getKind() == TypeKind.VOID) {
            diagnostics.error(getter, "@Provides requires a public no-argument getter");
            return;
        }
        if (owner.getAnnotation(Wired.class) == null || !mirrors.isSingleton(owner)) {
            diagnostics.error(getter, "@Provides getters must live on a @Wired singleton");
            return;
        }
        if (mirrors.isLazy(owner)) {
            diagnostics.error(getter, "@Provides getters cannot live on a lazy singleton");
            return;
        }
        String product = mirrors.erased(getter.getReturnType());
        if (!mirrors.isReferencable(getter.getReturnType())) {
            diagnostics.error(getter, "Product type must be public: " + product);
            return;
        }
        WiringModel.Product previous = model.addProduct(
                product,
                mirrors.qualifier(getter),
                new WiringModel.Product(
                        owner.getQualifiedName().toString(),
                        getter.getSimpleName().toString()));
        if (previous != null) diagnostics.error(getter, "Product is already provided by " + previous.owner());
    }

    void validateSingleton(TypeElement component) {
        if (component.getAnnotation(Wired.class) == null)
            diagnostics.error(component, "@Singleton classes must be @Wired");
    }

    void validateInitializes(TypeElement component) {
        if (component.getAnnotation(Wired.class) == null || !mirrors.isSingleton(component)) {
            diagnostics.error(component, "@Initializes requires a @Wired singleton");
            return;
        }
        if (mirrors.isLazy(component)) {
            diagnostics.error(component, "@Initializes cannot be used on a lazy singleton");
            return;
        }
        for (String holder : mirrors.holders(component.getAnnotation(Initializes.class)::value)) {
            requireStaticHolder(component, holder);
            String previous =
                    model.addInitializer(holder, component.getQualifiedName().toString());
            if (previous != null) diagnostics.error(component, "Holder is already initialized by " + previous);
        }
    }

    void validateRequires(TypeElement component) {
        if (component.getAnnotation(Wired.class) == null) {
            diagnostics.error(component, "@Requires classes must be @Wired");
            return;
        }
        if (mirrors.isLazy(component)) {
            diagnostics.error(component, "@Requires cannot be used on a lazy singleton");
            return;
        }
        List<String> holders = mirrors.holders(component.getAnnotation(Requires.class)::value);
        for (String holder : holders) requireStaticHolder(component, holder);
        model.addRequirements(component.getQualifiedName().toString(), holders);
    }

    private void requireStaticHolder(TypeElement anchor, String holder) {
        TypeElement element = mirrors.typeElement(holder);
        if (element != null && element.getAnnotation(StaticHolder.class) == null)
            diagnostics.error(anchor, "Holder must be annotated with @StaticHolder: " + holder);
    }

    void validateGraph() {
        if (registry == null) diagnostics.error("No @Registry class to generate the component registry for");
        graph.validate();
    }

    private void collect(TypeElement component, ExecutableElement constructor) {
        Set<String> seen = new HashSet<>();
        List<WiringModel.Parameter> parameters = new ArrayList<>();
        List<? extends VariableElement> constructorParameters = constructor.getParameters();
        for (int index = 0; index < constructorParameters.size(); index++) {
            VariableElement parameter = constructorParameters.get(index);
            WiringModel.Parameter collected = collectParameter(parameter, index);
            if (collected == null) continue;
            // Resolution is type-based, so a second parameter of the same type could only receive the same value
            if (!seen.add(collected.erasedClass() + "|" + collected.qualifier()))
                diagnostics.error(parameter, "Duplicate constructor parameter type: " + collected.erasedClass());
            parameters.add(collected);
        }
        Singleton singleton = component.getAnnotation(Singleton.class);
        model.addComponent(
                component.getQualifiedName().toString(),
                new WiringModel.Component(
                        singleton != null,
                        singleton != null && singleton.lazy(),
                        mirrors.qualifier(component),
                        parameters));
    }

    private WiringModel.Parameter collectParameter(VariableElement parameter, int index) {
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
            diagnostics.error(parameter, "Constructor parameter types must be public: " + erased);
            return null;
        }
        return new WiringModel.Parameter(
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

    private static List<ComponentRule> loadRules() {
        List<ComponentRule> rules = new ArrayList<>();
        for (ComponentRule rule : ServiceLoader.load(ComponentRule.class, ComponentRule.class.getClassLoader()))
            rules.add(rule);
        return rules;
    }
}
