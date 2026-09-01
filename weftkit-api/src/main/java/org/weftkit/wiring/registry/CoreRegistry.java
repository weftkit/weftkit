package org.weftkit.wiring.registry;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The core wiring every component needs: load order, constructor dependencies, factories, and
 * interface bindings. The stable heart of a {@link ComponentRegistry}, unaware of any feature
 * layered on top. Binding maps are nested by qualifier, with the empty string for unqualified
 * entries. All returned collections must be immutable.
 */
public interface CoreRegistry {

    List<Class<?>> loadOrder();

    Map<Class<?>, List<Dependency>> parameters();

    Map<Class<?>, Function<Object[], Object>> factories();

    Map<Class<?>, Map<String, Class<?>>> bindings();
}
