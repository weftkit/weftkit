package org.weftkit.wiring.processor.generate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.weftkit.wiring.processor.model.Component;
import org.weftkit.wiring.processor.model.Product;

// One package's share of the wiring: the entries the central registry cannot render because they
// name non-public types of that package
final class Fragment {

    private final Map<String, Component> components = new LinkedHashMap<>();

    private final List<String> lazySingletons = new ArrayList<>();

    private final Map<String, Map<String, String>> bindings = new TreeMap<>();

    private final Map<String, Map<String, Product>> products = new TreeMap<>();

    Map<String, Component> components() {
        return components;
    }

    List<String> lazySingletons() {
        return lazySingletons;
    }

    Map<String, Map<String, String>> bindings() {
        return bindings;
    }

    Map<String, Map<String, Product>> products() {
        return products;
    }
}
