package org.weftkit.wiring.registry;

import java.util.Map;
import java.util.function.Function;

/**
 * The {@code @Provides} products: which component owns each product, by qualifier, and how to read
 * it off that owner. Both maps are nested by qualifier, with the empty string for unqualified
 * entries.
 */
public interface ProductRegistry {

    Map<Class<?>, Map<String, Class<?>>> productOwners();

    Map<Class<?>, Map<String, Function<Object, Object>>> productGetters();
}
