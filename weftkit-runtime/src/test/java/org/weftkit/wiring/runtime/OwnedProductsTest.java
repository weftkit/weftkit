package org.weftkit.wiring.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.weftkit.wiring.registry.TestRegistry.on;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.weftkit.wiring.registry.TestRegistry;

class OwnedProductsTest {
    static class Resource implements AutoCloseable {
        boolean closed;

        @Override
        public void close() {
            closed = true;
        }
    }

    record First(Resource resource) {}

    record Second(Resource resource) {}

    @Test
    void eachConsumerGetsItsOwnResourceAndFactoriesAreNotCapturedAtProviderLoad() {
        List<Resource> created = new ArrayList<>();
        TestRegistry registry = new TestRegistry()
                .eager(Object.class, args -> new Object())
                .ownedProduct(Resource.class, Object.class, provider -> {
                    Resource resource = new Resource();
                    created.add(resource);
                    return resource;
                })
                .lazy(First.class, args -> new First((Resource) args[0]), on(Resource.class))
                .lazy(Second.class, args -> new Second((Resource) args[0]), on(Resource.class));
        WeftLoader loader = new WeftLoader(registry);
        assertTrue(loader.load());
        assertTrue(created.isEmpty());
        First first = loader.create(First.class);
        Second second = loader.create(Second.class);
        assertNotSame(first.resource(), second.resource());
        assertSame(first, loader.create(First.class));
        assertEquals(2, created.size());
        loader.unload();
        assertTrue(first.resource().closed);
        assertTrue(second.resource().closed);
    }

    @Test
    void failedLazyConsumerClosesItsResourceWithoutClosingASibling() {
        List<Resource> created = new ArrayList<>();
        WeftLoader loader = new WeftLoader(new TestRegistry()
                .eager(Object.class, args -> new Object())
                .ownedProduct(Resource.class, Object.class, provider -> {
                    Resource resource = new Resource();
                    created.add(resource);
                    return resource;
                })
                .eager(First.class, args -> new First((Resource) args[0]), on(Resource.class))
                .lazy(
                        Second.class,
                        args -> {
                            throw new IllegalStateException("failed");
                        },
                        on(Resource.class)));
        assertTrue(loader.load());
        assertThrows(RuntimeException.class, () -> loader.create(Second.class));
        assertFalse(loader.get(First.class).resource().closed);
        assertTrue(created.get(1).closed);
        assertTrue(loader.resetFailure(Second.class));
        assertThrows(RuntimeException.class, () -> loader.create(Second.class));
        assertNotSame(created.get(1), created.get(2));
        assertTrue(created.get(2).closed);
        loader.unload();
        assertTrue(created.get(0).closed);
    }
}
