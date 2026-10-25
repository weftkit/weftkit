package org.weftkit.wiring.processor.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.lang.model.element.AnnotationMirror;

public class WiringModel {

    private final Map<String, Component> components = new TreeMap<>();

    private final Map<String, Map<String, Product>> products = new TreeMap<>();

    private final Map<String, String> initializers = new TreeMap<>();

    private final Map<String, List<String>> requirements = new TreeMap<>();

    private final Map<String, Map<String, String>> bindings = new TreeMap<>();

    private final Map<String, String> hiddenTypes = new TreeMap<>();

    private final List<String> modules = new ArrayList<>();

    private final Map<String, String> settingsDefaults = new TreeMap<>();

    private final Set<String> flagsProviders = new LinkedHashSet<>();

    private final Set<String> ambientTypes = new LinkedHashSet<>();

    private final Set<String> argumentDependent = new LinkedHashSet<>();

    private final Map<String, Set<String>> ambientEdges = new TreeMap<>();

    private final Map<String, List<String>> dependencyCache = new HashMap<>();

    private final Map<String, List<AnnotationMirror>> handledAnnotations = new TreeMap<>();

    private final Map<String, Handler> handlers = new TreeMap<>();

    private final Map<String, Handler> replacedModules = new HashMap<>();

    public void addComponent(String component, Component definition) {
        components.put(component, definition);
    }

    public Product addProduct(String product, String qualifier, Product provider) {
        return products.computeIfAbsent(product, key -> new TreeMap<>()).put(qualifier, provider);
    }

    public String addInitializer(String holder, String initializer) {
        return initializers.put(holder, initializer);
    }

    public void addRequirements(String component, List<String> holders) {
        requirements.put(component, holders);
    }

    public void addBinding(String abstraction, String qualifier, String implementation) {
        bindings.computeIfAbsent(abstraction, key -> new TreeMap<>()).put(qualifier, implementation);
    }

    public void addModule(String module) {
        modules.add(module);
    }

    /** The woven module classes, declared before contributed. */
    public List<String> modules() {
        return Collections.unmodifiableList(modules);
    }

    public boolean isModule(String type) {
        return modules.contains(type);
    }

    /** Records a generated settings implementation, keyed by its {@code @ModuleSettings} interface. */
    public void addSettingsDefault(String settings, String implementation) {
        settingsDefaults.put(settings, implementation);
    }

    public Map<String, String> settingsDefaults() {
        return Collections.unmodifiableMap(settingsDefaults);
    }

    public void addFlagsProvider(String component) {
        flagsProviders.add(component);
    }

    /** Components implementing FeatureFlags, ordered before every flagged component. */
    public Set<String> flagsProviders() {
        return Collections.unmodifiableSet(flagsProviders);
    }

    public void addAmbientType(String type) {
        ambientTypes.add(type);
    }

    /** The declared ambient types: registry class, its constructor parameters, attribute, contributions. */
    public Set<String> ambientTypes() {
        return Collections.unmodifiableSet(ambientTypes);
    }

    /** Marks a plain component with a required dependency only explicit create arguments can fill. */
    public void markArgumentDependent(String component) {
        argumentDependent.add(component);
    }

    public boolean isArgumentDependent(String component) {
        return argumentDependent.contains(component);
    }

    /** Records that a component resolves a parameter through the ambient layer, keyed by injected type. */
    public void addAmbientEdge(String component, String ambientType) {
        ambientEdges.computeIfAbsent(component, key -> new LinkedHashSet<>()).add(ambientType);
    }

    /** Component to the ambient types it injects, rendered as external roots in the graph. */
    public Map<String, Set<String>> ambientEdges() {
        return Collections.unmodifiableMap(ambientEdges);
    }

    /** Records a component annotation whose type names its handling module through {@code @HandledBy}. */
    public void addHandledAnnotation(String component, AnnotationMirror annotation, String type, Handler handler) {
        handledAnnotations.computeIfAbsent(component, key -> new ArrayList<>()).add(annotation);
        handlers.put(type, handler);
    }

    public Map<String, List<AnnotationMirror>> handledAnnotations() {
        return Collections.unmodifiableMap(handledAnnotations);
    }

    /** Handled annotation type to the module its {@code @HandledBy} names. */
    public Map<String, Handler> handlers() {
        return Collections.unmodifiableMap(handlers);
    }

    /** Records that a module took over the id of another, so it handles that module's annotations. */
    public void replaceModule(String module, Handler replacement) {
        replacedModules.put(module, replacement);
    }

    /** The module handling an annotation type, the one that took over its id when replaced. */
    public Handler handler(String type) {
        Handler handler = handlers.get(type);
        return replacedModules.getOrDefault(handler.module(), handler);
    }

    public void markHidden(String type, String packageName) {
        hiddenTypes.put(type, packageName);
    }

    public boolean isHidden(String type) {
        return hiddenTypes.containsKey(type);
    }

    public String hiddenPackage(String type) {
        return hiddenTypes.get(type);
    }

    public boolean isComponent(String type) {
        return components.containsKey(type);
    }

    public boolean isProduct(String type, String qualifier) {
        return products.containsKey(type) && products.get(type).containsKey(qualifier);
    }

    public boolean isProductType(String type) {
        return products.containsKey(type);
    }

    public boolean isInitialized(String holder) {
        return initializers.containsKey(holder);
    }

    public boolean isEmpty() {
        return components.isEmpty();
    }

    public Map<String, Component> components() {
        return Collections.unmodifiableMap(components);
    }

    public Map<String, Map<String, Product>> products() {
        return Collections.unmodifiableMap(products);
    }

    public Map<String, String> initializers() {
        return Collections.unmodifiableMap(initializers);
    }

    public Map<String, List<String>> requirements() {
        return Collections.unmodifiableMap(requirements);
    }

    public Map<String, Map<String, String>> bindings() {
        return Collections.unmodifiableMap(bindings);
    }

    // Cached because the graph is walked repeatedly (load order, cycle check, graph render); safe
    // because every binding, product, and requirement is recorded before any traversal begins
    public List<String> dependencies(String component) {
        return dependencyCache.computeIfAbsent(component, this::computeDependencies);
    }

    private List<String> computeDependencies(String component) {
        List<String> dependencies = injectionDependencies(component);
        for (String holder : requirements.getOrDefault(component, List.of())) {
            String initializer = initializers.get(holder);
            if (initializer != null && isComponent(initializer)) dependencies.add(initializer);
        }
        // The gate resolves flags through the provider, so it must precede any flagged component
        if (components.get(component).featureFlag() != null && flagsProviders.size() == 1) {
            String provider = flagsProviders.iterator().next();
            if (!provider.equals(component) && isComponent(provider) && !dependencies.contains(provider))
                dependencies.add(provider);
        }
        return dependencies;
    }

    public List<String> injectionDependencies(String component) {
        return injectionDependencies(component, true);
    }

    // Dependencies that must resolve for the component to be constructed, an Optional<T> parameter
    // may be absent, so it is excluded. Consumed by the feature flag rule, which allows an optional
    // dependency on a flagged component but rejects a required one
    public List<String> requiredInjectionDependencies(String component) {
        return injectionDependencies(component, false);
    }

    private List<String> injectionDependencies(String component, boolean includeOptional) {
        List<String> dependencies = new ArrayList<>();
        for (Parameter parameter : components.get(component).parameters()) {
            if (!includeOptional && parameter.optional()) continue;
            String bound = binding(parameter);
            if (parameter.qualifier().isEmpty() && isComponent(parameter.erasedClass()))
                dependencies.add(parameter.erasedClass());
            else if (bound != null) dependencies.add(bound);
            else if (isProduct(parameter.erasedClass(), parameter.qualifier())) {
                String owner = products.get(parameter.erasedClass())
                        .get(parameter.qualifier())
                        .owner();
                if (isComponent(owner)) dependencies.add(owner);
            }
        }
        return dependencies;
    }

    public List<String> loadOrder() {
        List<String> order = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        components.forEach((component, definition) -> {
            if (eager(definition)) visit(component, visited, order);
        });
        return order;
    }

    public List<String> lazySingletons() {
        List<String> lazy = new ArrayList<>();
        components.forEach((component, definition) -> {
            if (definition.singleton() && definition.lazy()) lazy.add(component);
        });
        return lazy;
    }

    // Consumed by the runtime to create initializers before their dependents. Eager initializers
    // are included on purpose, turning a creation before their load into an explicit error
    // instead of a silently empty holder
    public Map<String, List<String>> componentRequirements() {
        Map<String, List<String>> resolved = new TreeMap<>();
        requirements.forEach((component, holders) -> {
            List<String> loaders = new ArrayList<>();
            for (String holder : holders) {
                String initializer = initializers.get(holder);
                if (initializer != null && isComponent(initializer) && !loaders.contains(initializer))
                    loaders.add(initializer);
            }
            if (!loaders.isEmpty()) resolved.put(component, loaders);
        });
        return resolved;
    }

    private String binding(Parameter parameter) {
        Map<String, String> qualified = bindings.get(parameter.erasedClass());
        return qualified == null ? null : qualified.get(parameter.qualifier());
    }

    private void visit(String component, Set<String> visited, List<String> order) {
        if (!visited.add(component)) return;
        for (String dependency : dependencies(component)) visit(dependency, visited, order);
        if (eager(components.get(component))) order.add(component);
    }

    private boolean eager(Component definition) {
        return definition.singleton() && !definition.lazy();
    }
}
