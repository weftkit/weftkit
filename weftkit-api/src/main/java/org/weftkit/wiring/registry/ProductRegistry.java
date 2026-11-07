package org.weftkit.wiring.registry;

import java.util.Map;
import java.util.function.Function;

/**
 * Product providers, shared getters, and per-consumer resource factories. Maps are nested by
 * qualifier, with the empty string for unqualified entries.
 */
public interface ProductRegistry {

    Map<Class<?>, Map<String, Class<?>>> productOwners();

    Map<Class<?>, Map<String, Function<Object, Object>>> productGetters();

    /** Factories for per-consumer resources, excluded from shared product capture. */
    default Map<Class<?>, Map<String, Function<Object, Object>>> ownedProductGetters() {
        return Map.of();
    }
}
