package org.weftkit.wiring.runtime;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import org.weftkit.wiring.registry.ProductRegistry;
import org.weftkit.wiring.runtime.exception.ResolutionException;

/**
 * The {@code @Provides} product feature: captures each singleton's products as it loads, serves
 * them to dependents, and drops them when the owner is rolled back or the graph unloads. It
 * observes the component core's creation lifecycle, so the core stays unaware products exist.
 */
final class Products implements CreationListener {

    private final ProductRegistry registry;

    // Captured product values, nested product type then qualifier, mirroring the registry maps
    private final Map<Class<?>, Map<String, Object>> store = new HashMap<>();

    Products(ProductRegistry registry) {
        this.registry = registry;
    }

    // Captures aside and commits only once every product checks out, keeping onLoaded atomic
    @Override
    public void onLoaded(Class<?> owner, Object singleton) {
        Map<Class<?>, Map<String, Object>> captured = new HashMap<>();
        forEachProductOf(owner, (product, qualifier) -> {
            Object value = registry.productGetters().get(product).get(qualifier).apply(singleton);
            if (value == null)
                throw new ResolutionException(
                        "Product " + product.getName() + " of " + owner.getName() + " is null after load");
            captured.computeIfAbsent(product, key -> new HashMap<>()).put(qualifier, value);
        });
        captured.forEach((product, values) ->
                store.computeIfAbsent(product, key -> new HashMap<>()).putAll(values));
    }

    @Override
    public void onDropped(Class<?> owner) {
        forEachProductOf(owner, (product, qualifier) -> {
            Map<String, Object> values = store.get(product);
            if (values != null) values.remove(qualifier);
        });
    }

    @Override
    public void reset() {
        store.clear();
    }

    /** The component that provides the product, or null when nothing does. */
    Class<?> owner(Class<?> product, String qualifier) {
        Map<String, Class<?>> owners = registry.productOwners().get(product);
        return owners == null ? null : owners.get(qualifier);
    }

    /** The captured product value, or null when its owner has not loaded yet. */
    Object captured(Class<?> product, String qualifier) {
        Map<String, Object> values = store.get(product);
        return values == null ? null : values.get(qualifier);
    }

    /** Reads the product straight off a live provider, for an owner still inside its load hook. */
    Object extract(Class<?> product, String qualifier, Object provider) {
        return registry.productGetters().get(product).get(qualifier).apply(provider);
    }

    private void forEachProductOf(Class<?> owner, BiConsumer<Class<?>, String> action) {
        registry.productOwners()
                .forEach((product, qualified) -> qualified.forEach((qualifier, type) -> {
                    if (type.equals(owner)) action.accept(product, qualifier);
                }));
    }
}
