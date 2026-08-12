package org.weftkit.wiring.processor.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class WiringModel {

    private final Map<String, Component> components = new TreeMap<>();

    private final Map<String, Map<String, Product>> products = new TreeMap<>();

    private final Map<String, String> initializers = new TreeMap<>();

    private final Map<String, List<String>> requirements = new TreeMap<>();

    private final Map<String, Map<String, String>> bindings = new TreeMap<>();

    private final Map<String, String> hiddenTypes = new TreeMap<>();

    private final Map<String, List<String>> dependencyCache = new HashMap<>();

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
        return dependencies;
    }

    public List<String> injectionDependencies(String component) {
        List<String> dependencies = new ArrayList<>();
        for (Parameter parameter : components.get(component).parameters()) {
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
