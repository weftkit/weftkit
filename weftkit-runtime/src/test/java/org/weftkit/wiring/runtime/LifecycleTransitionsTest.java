package org.weftkit.wiring.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.weftkit.wiring.Loader;
import org.weftkit.wiring.registry.TestRegistry;

class LifecycleTransitionsTest {

    private WeftLoader loader;
    private final AtomicInteger unloads = new AtomicInteger();

    @Test
    void unloadDuringEagerLoadIsRejectedWithoutInterruptingStartup() {
        loader = new WeftLoader(new TestRegistry()
                .eager(
                        Resource.class,
                        args -> new Resource(() -> assertThrows(IllegalStateException.class, loader::unload))));
        assertTrue(loader.load());
        assertNotNull(loader.get(Resource.class));
        loader.unload();
        assertEquals(1, unloads.get());
    }

    @Test
    void unloadDuringALazyLoadHookIsRejectedWithoutDroppingTheGraph() {
        loader = new WeftLoader(new TestRegistry()
                .lazy(
                        Resource.class,
                        args -> new Resource(() -> assertThrows(IllegalStateException.class, loader::unload))));
        assertTrue(loader.load());
        assertNotNull(loader.create(Resource.class));
        assertNotNull(loader.get(Resource.class));
        loader.unload();
        assertEquals(1, unloads.get());
    }

    @Test
    void unloadDuringALazyConstructorIsRejectedBeforeAnyHookRuns() {
        loader = new WeftLoader(new TestRegistry().lazy(Resource.class, args -> {
            assertThrows(IllegalStateException.class, loader::unload);
            return new Resource(() -> {});
        }));
        assertTrue(loader.load());
        assertNotNull(loader.create(Resource.class));
        loader.unload();
        assertEquals(1, unloads.get());
    }

    @Test
    void recursiveUnloadIsRejectedAndTheOuterTeardownCompletes() {
        loader = new WeftLoader(new TestRegistry().eager(Resource.class, args -> new Resource(() -> {}) {
            @Override
            public void unload() {
                super.unload();
                assertThrows(IllegalStateException.class, loader::unload);
                assertThrows(IllegalStateException.class, loader::load);
            }
        }));
        assertTrue(loader.load());
        loader.unload();
        assertNull(loader.get(Resource.class));
        assertEquals(1, unloads.get());
        loader.unload();
        assertEquals(1, unloads.get());
        assertTrue(loader.load());
    }

    private class Resource implements Loader {
        private final Runnable duringLoad;

        Resource(Runnable duringLoad) {
            this.duringLoad = duringLoad;
        }

        @Override
        public boolean load() {
            duringLoad.run();
            return true;
        }

        @Override
        public void unload() {
            unloads.incrementAndGet();
        }
    }
}
