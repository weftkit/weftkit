package org.weftkit.wiring.processor.generate;

import java.util.Map;
import java.util.Set;

// How a registry section is built and which Registries helper folds fragment contributions in
enum Container {
    SET(Set.class, "of", "union"),
    MAP(Map.class, "ofEntries", "merge"),
    NESTED_MAP(Map.class, "ofEntries", "mergeNested");

    private final Class<?> type;

    private final String factory;

    private final String merger;

    Container(Class<?> type, String factory, String merger) {
        this.type = type;
        this.factory = factory;
        this.merger = merger;
    }

    Class<?> type() {
        return type;
    }

    String factory() {
        return factory;
    }

    String merger() {
        return merger;
    }
}
