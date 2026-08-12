package org.weftkit.wiring.processor.generate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.weftkit.wiring.processor.model.Component;
import org.weftkit.wiring.processor.model.Parameter;
import org.weftkit.wiring.processor.model.Product;
import org.weftkit.wiring.processor.model.WiringModel;

// Splits the wiring into what the central registry may render and what must render inside a
// package fragment because it names non-public types. Every entry involving a hidden type from
// outside the registry package moves to that type's package
class Fragments {

    private final WiringModel model;

    private final String registryPackage;

    private final Map<String, Fragment> byPackage = new TreeMap<>();

    private final Set<String> fragmented = new HashSet<>();

    private final Map<String, Map<String, String>> centralBindings = new TreeMap<>();

    private final Map<String, Map<String, Product>> centralProducts = new TreeMap<>();

    private final List<String> centralLazySingletons = new ArrayList<>();

    Fragments(WiringModel model, String registryPackage) {
        this.model = model;
        this.registryPackage = registryPackage;
        model.components().forEach((component, definition) -> {
            String packageName = componentPackage(component, definition);
            if (packageName == null) return;
            fragment(packageName).components().put(component, definition);
            fragmented.add(component);
        });
        for (String singleton : model.lazySingletons()) {
            String packageName = fragmentPackage(singleton);
            if (packageName == null) centralLazySingletons.add(singleton);
            else fragment(packageName).lazySingletons().add(singleton);
        }
        model.bindings()
                .forEach((abstraction, qualified) -> qualified.forEach((qualifier, implementation) -> {
                    String packageName = fragmentPackage(abstraction);
                    if (packageName == null) packageName = fragmentPackage(implementation);
                    Map<String, Map<String, String>> target = packageName == null
                            ? centralBindings
                            : fragment(packageName).bindings();
                    target.computeIfAbsent(abstraction, key -> new TreeMap<>()).put(qualifier, implementation);
                }));
        model.products()
                .forEach((product, qualified) -> qualified.forEach((qualifier, provider) -> {
                    String packageName = fragmentPackage(product);
                    if (packageName == null) packageName = fragmentPackage(provider.owner());
                    Map<String, Map<String, Product>> target = packageName == null
                            ? centralProducts
                            : fragment(packageName).products();
                    target.computeIfAbsent(product, key -> new TreeMap<>()).put(qualifier, provider);
                }));
    }

    Map<String, Fragment> byPackage() {
        return byPackage;
    }

    boolean isFragmented(String component) {
        return fragmented.contains(component);
    }

    Map<String, Map<String, String>> centralBindings() {
        return centralBindings;
    }

    Map<String, Map<String, Product>> centralProducts() {
        return centralProducts;
    }

    List<String> centralLazySingletons() {
        return centralLazySingletons;
    }

    // Hidden types in the registry package need no fragment, the central class shares their package
    String fragmentPackage(String type) {
        if (!model.isHidden(type)) return null;
        String packageName = model.hiddenPackage(type);
        return packageName.equals(registryPackage) ? null : packageName;
    }

    // The factory and parameter list must render where every named type is accessible, which is
    // the component's own package as soon as the component or one of its parameters is non-public
    private String componentPackage(String component, Component definition) {
        String packageName = fragmentPackage(component);
        if (packageName != null) return packageName;
        for (Parameter parameter : definition.parameters()) {
            packageName = fragmentPackage(parameter.erasedClass());
            if (packageName != null) return packageName;
        }
        return null;
    }

    private Fragment fragment(String packageName) {
        return byPackage.computeIfAbsent(packageName, key -> new Fragment());
    }
}
