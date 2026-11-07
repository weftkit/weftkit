package org.weftkit.wiring.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/** A hand-built {@link ComponentRegistry} for tests, eager components in call order. */
public final class TestRegistry implements ComponentRegistry {

    private final List<Class<?>> loadOrder = new ArrayList<>();

    private final Set<Class<?>> lazySingletons = new LinkedHashSet<>();

    private final Map<Class<?>, List<Dependency>> parameters = new LinkedHashMap<>();

    private final Map<Class<?>, Function<Object[], Object>> factories = new LinkedHashMap<>();

    private final Map<Class<?>, Map<String, Class<?>>> productOwners = new LinkedHashMap<>();

    private final Map<Class<?>, Map<String, Function<Object, Object>>> productGetters = new LinkedHashMap<>();

    private final Map<Class<?>, Map<String, Function<Object, Object>>> ownedProductGetters = new LinkedHashMap<>();

    private final Map<Class<?>, Flag> featureFlags = new LinkedHashMap<>();

    private final Map<Class<?>, List<Class<?>>> requirements = new LinkedHashMap<>();

    private final List<Class<?>> modules = new ArrayList<>();

    private final List<Class<?>> ambientTypes = new ArrayList<>();

    private Supplier<Map<Class<?>, Map<String, Class<?>>>> bindings = Map::of;

    public TestRegistry() {}

    public static Dependency on(Class<?> type) {
        return new Dependency(type, "", false);
    }

    public TestRegistry eager(Class<?> type, Function<Object[], Object> factory, Dependency... dependencies) {
        loadOrder.add(type);
        return component(type, factory, dependencies);
    }

    public TestRegistry lazy(Class<?> type, Function<Object[], Object> factory, Dependency... dependencies) {
        lazySingletons.add(type);
        return component(type, factory, dependencies);
    }

    public TestRegistry plain(Class<?> type, Function<Object[], Object> factory, Dependency... dependencies) {
        return component(type, factory, dependencies);
    }

    public TestRegistry product(Class<?> product, Class<?> owner, Function<Object, Object> getter) {
        productOwners.computeIfAbsent(product, key -> new LinkedHashMap<>()).put("", owner);
        productGetters.computeIfAbsent(product, key -> new LinkedHashMap<>()).put("", getter);
        return this;
    }

    public TestRegistry ownedProduct(Class<?> product, Class<?> owner, Function<Object, Object> factory) {
        productOwners.computeIfAbsent(product, key -> new LinkedHashMap<>()).put("", owner);
        ownedProductGetters
                .computeIfAbsent(product, key -> new LinkedHashMap<>())
                .put("", factory);
        return this;
    }

    public TestRegistry flag(Class<?> type, Flag flag) {
        featureFlags.put(type, flag);
        return this;
    }

    public TestRegistry requires(Class<?> type, Class<?>... holders) {
        requirements.put(type, List.of(holders));
        return this;
    }

    public TestRegistry module(Class<?> type) {
        modules.add(type);
        return this;
    }

    public TestRegistry ambient(Class<?>... types) {
        Collections.addAll(ambientTypes, types);
        return this;
    }

    public TestRegistry bindings(Supplier<Map<Class<?>, Map<String, Class<?>>>> bindings) {
        this.bindings = bindings;
        return this;
    }

    private TestRegistry component(Class<?> type, Function<Object[], Object> factory, Dependency... dependencies) {
        parameters.put(type, List.of(dependencies));
        factories.put(type, factory);
        return this;
    }

    @Override
    public List<Class<?>> loadOrder() {
        return Collections.unmodifiableList(loadOrder);
    }

    @Override
    public Set<Class<?>> lazySingletons() {
        return Collections.unmodifiableSet(lazySingletons);
    }

    @Override
    public Map<Class<?>, List<Dependency>> parameters() {
        return Collections.unmodifiableMap(parameters);
    }

    @Override
    public Map<Class<?>, Function<Object[], Object>> factories() {
        return Collections.unmodifiableMap(factories);
    }

    @Override
    public Map<Class<?>, Map<String, Class<?>>> bindings() {
        return bindings.get();
    }

    @Override
    public Map<Class<?>, Map<String, Class<?>>> productOwners() {
        return Collections.unmodifiableMap(productOwners);
    }

    @Override
    public Map<Class<?>, Map<String, Function<Object, Object>>> productGetters() {
        return Collections.unmodifiableMap(productGetters);
    }

    @Override
    public Map<Class<?>, Map<String, Function<Object, Object>>> ownedProductGetters() {
        return Collections.unmodifiableMap(ownedProductGetters);
    }

    @Override
    public Map<Class<?>, Flag> featureFlags() {
        return Collections.unmodifiableMap(featureFlags);
    }

    @Override
    public Map<Class<?>, List<Class<?>>> requirements() {
        return Collections.unmodifiableMap(requirements);
    }

    @Override
    public List<Class<?>> modules() {
        return Collections.unmodifiableList(modules);
    }

    @Override
    public List<Class<?>> ambientTypes() {
        return Collections.unmodifiableList(ambientTypes);
    }
}
