package org.weftkit.wiring.processor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypesException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import org.weftkit.wiring.Initializes;
import org.weftkit.wiring.Loader;
import org.weftkit.wiring.Qualified;
import org.weftkit.wiring.Requires;
import org.weftkit.wiring.Singleton;
import org.weftkit.wiring.StaticHolder;
import org.weftkit.wiring.Wired;
import org.weftkit.wiring.processor.spi.ComponentRule;

class WiredValidator {

    private static final String LOADER = Loader.class.getCanonicalName();

    private final ProcessingEnvironment processingEnv;

    private final WiringModel model;

    private final Set<String> sources = new HashSet<>();

    private final List<ComponentRule> rules = loadRules();

    private final StaticHolderCheck holderCheck;

    private String registry;

    private String registryPackage;

    private List<TypeMirror> ambient = List.of();

    WiredValidator(ProcessingEnvironment processingEnv, WiringModel model) {
        this.processingEnv = processingEnv;
        this.model = model;
        this.holderCheck = new StaticHolderCheck(processingEnv);
    }

    void addSources(RoundEnvironment roundEnvironment) {
        for (TypeElement type : ElementFilter.typesIn(roundEnvironment.getRootElements()))
            sources.add(type.getQualifiedName().toString());
    }

    String registryPackage() {
        return registryPackage;
    }

    void validateRegistry(TypeElement element) {
        String name = element.getQualifiedName().toString();
        if (registry != null && !registry.equals(name)) {
            error(element, "@Registry is already declared on " + registry);
            return;
        }
        List<ExecutableElement> constructors = publicConstructors(element);
        if (constructors.size() != 1) {
            error(element, "@Registry classes need exactly one public constructor");
            return;
        }
        registry = name;
        registryPackage = processingEnv
                .getElementUtils()
                .getPackageOf(element)
                .getQualifiedName()
                .toString();
        ambient = Stream.concat(
                        Stream.of(element.asType()),
                        constructors.get(0).getParameters().stream().map(VariableElement::asType))
                .toList();
    }

    void validateComponent(TypeElement component) {
        if (component.getKind() != ElementKind.CLASS || component.getModifiers().contains(Modifier.ABSTRACT)) {
            error(component, "@Wired requires a concrete class");
            return;
        }
        // Type parameters erase to their bounds, so resolution could match arbitrary arguments
        if (!component.getTypeParameters().isEmpty()) {
            error(component, "@Wired classes cannot be generic");
            return;
        }
        if (!isInstantiable(component)) {
            error(component, "@Wired classes must be public and top-level or static nested");
            return;
        }
        List<ExecutableElement> constructors = publicConstructors(component);
        if (constructors.size() != 1) {
            error(component, "@Wired components need exactly one public constructor");
            return;
        }
        // A per-injection component would never have its load() called
        if (isSubtype(component, LOADER) && !isSingleton(component)) {
            error(component, "Loader implementations must be @Singleton");
            return;
        }
        if (isLazy(component) && isSubtype(component, LOADER)) {
            error(component, "Lazy singletons cannot implement Loader");
            return;
        }
        for (ComponentRule rule : rules) rule.validate(component, processingEnv);
        holderCheck.check(component, constructors.get(0), isSubtype(component, LOADER), declaredHolders(component));
        collect(component, constructors.get(0));
    }

    private Set<String> declaredHolders(TypeElement component) {
        Set<String> declared = new HashSet<>();
        Requires requires = component.getAnnotation(Requires.class);
        if (requires != null) declared.addAll(holders(requires::value));
        Initializes initializes = component.getAnnotation(Initializes.class);
        if (initializes != null) declared.addAll(holders(initializes::value));
        return declared;
    }

    void validateProduct(ExecutableElement getter) {
        TypeElement owner = (TypeElement) getter.getEnclosingElement();
        if (!getter.getModifiers().contains(Modifier.PUBLIC)
                || !getter.getParameters().isEmpty()
                || getter.getReturnType().getKind() == TypeKind.VOID) {
            error(getter, "@Provides requires a public no-argument getter");
            return;
        }
        if (owner.getAnnotation(Wired.class) == null || !isSingleton(owner)) {
            error(getter, "@Provides getters must live on a @Wired singleton");
            return;
        }
        if (isLazy(owner)) {
            error(getter, "@Provides getters cannot live on a lazy singleton");
            return;
        }
        String product =
                processingEnv.getTypeUtils().erasure(getter.getReturnType()).toString();
        if (!isReferencable(getter.getReturnType())) {
            error(getter, "Product type must be public: " + product);
            return;
        }
        WiringModel.Product previous = model.addProduct(
                product,
                qualifier(getter),
                new WiringModel.Product(
                        owner.getQualifiedName().toString(),
                        getter.getSimpleName().toString()));
        if (previous != null) error(getter, "Product is already provided by " + previous.owner());
    }

    void validateSingleton(TypeElement component) {
        if (component.getAnnotation(Wired.class) == null) error(component, "@Singleton classes must be @Wired");
    }

    void validateInitializes(TypeElement component) {
        if (component.getAnnotation(Wired.class) == null || !isSingleton(component)) {
            error(component, "@Initializes requires a @Wired singleton");
            return;
        }
        if (isLazy(component)) {
            error(component, "@Initializes cannot be used on a lazy singleton");
            return;
        }
        for (String holder : holders(component.getAnnotation(Initializes.class)::value)) {
            requireStaticHolder(component, holder);
            String previous =
                    model.addInitializer(holder, component.getQualifiedName().toString());
            if (previous != null) error(component, "Holder is already initialized by " + previous);
        }
    }

    void validateRequires(TypeElement component) {
        if (component.getAnnotation(Wired.class) == null) {
            error(component, "@Requires classes must be @Wired");
            return;
        }
        if (isLazy(component)) {
            error(component, "@Requires cannot be used on a lazy singleton");
            return;
        }
        List<String> holders = holders(component.getAnnotation(Requires.class)::value);
        for (String holder : holders) requireStaticHolder(component, holder);
        model.addRequirements(component.getQualifiedName().toString(), holders);
    }

    private void requireStaticHolder(Element anchor, String holder) {
        TypeElement element = processingEnv.getElementUtils().getTypeElement(holder);
        if (element != null && element.getAnnotation(StaticHolder.class) == null)
            error(anchor, "Holder must be annotated with @StaticHolder: " + holder);
    }

    // Class values in annotations are only accessible as mirrors during processing
    private List<String> holders(Supplier<Class<?>[]> value) {
        try {
            value.get();
            return List.of();
        } catch (MirroredTypesException ex) {
            return ex.getTypeMirrors().stream()
                    .map(mirror -> processingEnv.getTypeUtils().erasure(mirror).toString())
                    .toList();
        }
    }

    // Only external types can arrive as explicit arguments, so anything from this build must be resolvable
    void validateGraph() {
        if (registry == null)
            processingEnv
                    .getMessager()
                    .printMessage(Diagnostic.Kind.ERROR, "No @Registry class to generate the component registry for");
        for (Map.Entry<String, WiringModel.Component> component :
                model.components().entrySet()) {
            TypeElement element = processingEnv.getElementUtils().getTypeElement(component.getKey());
            List<? extends VariableElement> constructorParameters = constructorParameters(element);
            for (WiringModel.Parameter parameter : component.getValue().parameters()) {
                Element anchor = anchor(element, constructorParameters, parameter.index());
                if (model.isComponent(parameter.erasedClass())) {
                    if (!parameter.qualifier().isEmpty())
                        error(
                                anchor,
                                "Qualified dependency cannot target a concrete component: " + parameter.erasedClass());
                    continue;
                }
                if (parameter.singleton())
                    error(anchor, "Singleton dependency is not @Wired: " + parameter.erasedClass());
                else if (model.isProduct(parameter.erasedClass(), parameter.qualifier())) continue;
                else if (parameter.internal()) bind(parameter, anchor);
                else bindExternal(parameter, anchor);
            }
        }
        model.products().forEach((type, qualified) -> {
            if (model.isComponent(type))
                qualified
                        .values()
                        .forEach(product ->
                                error(getterAnchor(product), "Product type is also a @Wired component: " + type));
        });
        for (Map.Entry<String, String> initializer : model.initializers().entrySet()) {
            String holder = initializer.getKey();
            Element anchor = processingEnv.getElementUtils().getTypeElement(initializer.getValue());
            if (model.isComponent(holder)) error(anchor, "Initialized holder is also a @Wired component: " + holder);
            else if (model.isProductType(holder))
                error(anchor, "Initialized holder is also a @Provides product: " + holder);
        }
        for (Map.Entry<String, List<String>> requirement : model.requirements().entrySet()) {
            Element anchor = processingEnv.getElementUtils().getTypeElement(requirement.getKey());
            for (String holder : requirement.getValue()) {
                if (!model.isInitialized(holder)) error(anchor, "No @Wired loader initializes: " + holder);
            }
        }
        Set<String> cleared = new HashSet<>();
        for (String component : model.components().keySet()) checkCycle(component, new ArrayList<>(), cleared);
    }

    // cleared holds nodes whose whole subgraph is proven acyclic, so shared subgraphs are walked once;
    // a cycle member can never be cleared without its back edge first being caught by the path check
    private void checkCycle(String component, List<String> path, Set<String> cleared) {
        if (cleared.contains(component)) return;
        if (path.contains(component)) {
            error(
                    processingEnv.getElementUtils().getTypeElement(component),
                    "Component dependency cycle: " + String.join(" -> ", path) + " -> " + component);
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
            error(
                    anchor,
                    "Ambiguous dependency " + parameter.erasedClass() + ", implemented by "
                            + String.join(", ", implementations));
        else if (!parameter.optional())
            error(anchor, "Dependency must be @Wired or a @Provides product: " + parameter.erasedClass());
    }

    // External abstractions may arrive as explicit arguments, so bind only when a single
    // implementation makes the choice unambiguous. Zero implementations is the normal ambient case
    // and stays silent; several is a latent runtime failure worth flagging without forcing a choice.
    private void bindExternal(WiringModel.Parameter parameter, Element anchor) {
        List<String> implementations = implementations(parameter.erasedClass(), parameter.qualifier());
        if (implementations.size() == 1)
            model.addBinding(parameter.erasedClass(), parameter.qualifier(), implementations.get(0));
        else if (implementations.size() > 1)
            warning(
                    anchor,
                    "External dependency " + parameter.erasedClass() + " has multiple @Wired implementations ("
                            + String.join(", ", implementations)
                            + ") and is left unbound; supply one as a @Registry argument or ambient value at runtime");
    }

    private List<String> implementations(String abstraction, String qualifier) {
        TypeElement target = processingEnv.getElementUtils().getTypeElement(abstraction);
        if (target == null
                || (target.getKind() != ElementKind.INTERFACE
                        && !target.getModifiers().contains(Modifier.ABSTRACT))) return List.of();
        List<String> implementations = new ArrayList<>();
        for (Map.Entry<String, WiringModel.Component> component :
                model.components().entrySet()) {
            if (!qualifier.isEmpty() && !qualifier.equals(component.getValue().qualifier())) continue;
            TypeElement element = processingEnv.getElementUtils().getTypeElement(component.getKey());
            if (element != null && processingEnv.getTypeUtils().isAssignable(element.asType(), target.asType()))
                implementations.add(component.getKey());
        }
        return implementations;
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
                error(parameter, "Duplicate constructor parameter type: " + collected.erasedClass());
            parameters.add(collected);
        }
        Singleton singleton = component.getAnnotation(Singleton.class);
        model.addComponent(
                component.getQualifiedName().toString(),
                new WiringModel.Component(
                        singleton != null, singleton != null && singleton.lazy(), qualifier(component), parameters));
    }

    private WiringModel.Parameter collectParameter(VariableElement parameter, int index) {
        TypeMirror type = parameter.asType();
        boolean optional = isOptional(type);
        if (optional) {
            type = optionalArgument(type);
            if (type == null) {
                error(parameter, "Optional dependencies need a concrete type argument");
                return null;
            }
        }
        String erased = processingEnv.getTypeUtils().erasure(type).toString();
        if (type.getKind().isPrimitive()) {
            error(parameter, "@Wired constructors cannot take primitive parameters");
            return null;
        }
        if (!isReferencable(type)) {
            error(parameter, "Constructor parameter types must be public: " + erased);
            return null;
        }
        return new WiringModel.Parameter(
                erased, type.toString(), qualifier(parameter), isSingleton(type), isInternal(type), optional, index);
    }

    private boolean isOptional(TypeMirror type) {
        return processingEnv.getTypeUtils().erasure(type).toString().equals("java.util.Optional");
    }

    private TypeMirror optionalArgument(TypeMirror type) {
        if (!(type instanceof DeclaredType declared)
                || declared.getTypeArguments().size() != 1) return null;
        TypeMirror argument = declared.getTypeArguments().get(0);
        if (!(argument instanceof DeclaredType) || isOptional(argument)) return null;
        return argument;
    }

    private String qualifier(Element element) {
        Qualified qualified = element.getAnnotation(Qualified.class);
        return qualified == null ? "" : qualified.value();
    }

    private List<ExecutableElement> publicConstructors(TypeElement type) {
        return ElementFilter.constructorsIn(type.getEnclosedElements()).stream()
                .filter(constructor -> constructor.getModifiers().contains(Modifier.PUBLIC))
                .toList();
    }

    private List<? extends VariableElement> constructorParameters(TypeElement component) {
        List<ExecutableElement> constructors = publicConstructors(component);
        return constructors.size() == 1 ? constructors.get(0).getParameters() : List.of();
    }

    private Element anchor(TypeElement component, List<? extends VariableElement> parameters, int index) {
        return index >= 0 && index < parameters.size() ? parameters.get(index) : component;
    }

    private Element getterAnchor(WiringModel.Product product) {
        TypeElement owner = processingEnv.getElementUtils().getTypeElement(product.owner());
        return ElementFilter.methodsIn(owner.getEnclosedElements()).stream()
                .filter(method -> method.getSimpleName().contentEquals(product.getter()))
                .findFirst()
                .map(Element.class::cast)
                .orElse(owner);
    }

    private boolean isInstantiable(TypeElement component) {
        for (Element element = component;
                element instanceof TypeElement type;
                element = element.getEnclosingElement()) {
            if (!type.getModifiers().contains(Modifier.PUBLIC)) return false;
            if (type.getNestingKind() == NestingKind.MEMBER
                    && !type.getModifiers().contains(Modifier.STATIC)) return false;
            if (type.getNestingKind() != NestingKind.TOP_LEVEL && type.getNestingKind() != NestingKind.MEMBER)
                return false;
        }
        return true;
    }

    private boolean isReferencable(TypeMirror type) {
        for (Element element = processingEnv
                        .getTypeUtils()
                        .asElement(processingEnv.getTypeUtils().erasure(type));
                element instanceof TypeElement typeElement;
                element = element.getEnclosingElement()) {
            if (!typeElement.getModifiers().contains(Modifier.PUBLIC)) return false;
        }
        return true;
    }

    // Resolution is an exact type lookup, so only the declared type itself being a singleton counts
    private boolean isSingleton(TypeMirror type) {
        Element element = processingEnv
                .getTypeUtils()
                .asElement(processingEnv.getTypeUtils().erasure(type));
        return element instanceof TypeElement singleton && isSingleton(singleton);
    }

    private boolean isSingleton(TypeElement component) {
        return component.getAnnotation(Singleton.class) != null;
    }

    private boolean isLazy(TypeElement component) {
        Singleton singleton = component.getAnnotation(Singleton.class);
        return singleton != null && singleton.lazy();
    }

    // Internal means declared in this compilation and not already satisfied by a @Registry constructor argument
    private boolean isInternal(TypeMirror type) {
        for (TypeMirror provided : ambient) if (processingEnv.getTypeUtils().isAssignable(provided, type)) return false;
        for (Element element = processingEnv.getTypeUtils().asElement(type);
                element instanceof TypeElement typeElement;
                element = element.getEnclosingElement()) {
            if (sources.contains(typeElement.getQualifiedName().toString())) return true;
        }
        return false;
    }

    private boolean isSubtype(TypeElement type, String supertype) {
        TypeMirror mirror = mirror(supertype);
        return mirror != null && processingEnv.getTypeUtils().isAssignable(type.asType(), mirror);
    }

    private TypeMirror mirror(String qualifiedName) {
        TypeElement element = processingEnv.getElementUtils().getTypeElement(qualifiedName);
        return element == null ? null : element.asType();
    }

    private void error(Element element, String message) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element);
    }

    private void warning(Element element, String message) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, message, element);
    }

    private static List<ComponentRule> loadRules() {
        List<ComponentRule> rules = new ArrayList<>();
        for (ComponentRule rule : ServiceLoader.load(ComponentRule.class, ComponentRule.class.getClassLoader()))
            rules.add(rule);
        return rules;
    }
}
