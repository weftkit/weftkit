package org.weftkit.wiring.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.weftkit.wiring.runtime.exception.ResolutionException;

/** Injectable singleton ownership. Resources close in reverse registration order, before unload. */
public final class WeftScope implements AutoCloseable {
    private final List<AutoCloseable> resources = new ArrayList<>();
    private final Map<ProductKey, Object> products = new HashMap<>();
    private boolean closed;

    private record ProductKey(Class<?> type, String qualifier) {}

    WeftScope() {}

    /** Registers a resource for automatic cleanup. Rejects registration after close. */
    public synchronized <T extends AutoCloseable> T own(T resource) {
        Objects.requireNonNull(resource, "resource");
        if (closed) throw new IllegalStateException("Component scope is closed");
        resources.add(resource);
        return resource;
    }

    synchronized Object product(Class<?> type, String qualifier, Supplier<Object> factory) {
        if (closed) throw new IllegalStateException("Component scope is closed");
        ProductKey key = new ProductKey(type, qualifier);
        Object existing = products.get(key);
        if (existing != null) return existing;
        Object value = factory.get();
        if (!(value instanceof AutoCloseable resource))
            throw new ResolutionException("Owned product must be a non-null AutoCloseable: " + type.getName());
        own(resource);
        products.put(key, value);
        return value;
    }

    /** Closes once, attempting every resource even if one fails. */
    @Override
    public void close() {
        List<AutoCloseable> owned;
        synchronized (this) {
            if (closed) return;
            closed = true;
            owned = List.copyOf(resources);
            resources.clear();
            products.clear();
        }
        Throwable failure = null;
        for (int index = owned.size() - 1; index >= 0; index--) {
            try {
                owned.get(index).close();
            } catch (Exception | Error ex) {
                if (failure == null) failure = ex;
                else if (failure != ex) failure.addSuppressed(ex);
            }
        }
        if (failure instanceof Error error) throw error;
        if (failure instanceof RuntimeException runtime) throw runtime;
        if (failure != null) throw new IllegalStateException("Component resource cleanup failed", failure);
    }
}
