package org.weftkit.wiring.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.weftkit.wiring.Loader;
import org.weftkit.wiring.ModuleLifecycle;
import org.weftkit.wiring.registry.TestRegistry;

class CleanupFailuresTest {
    @Test
    void repeatedExceptionDoesNotInterruptRemainingComponentCleanup() {
        RuntimeException shared = new IllegalStateException("shared");
        List<String> events = new ArrayList<>();
        WeftLoader loader = new WeftLoader(new TestRegistry()
                .eager(First.class, args -> new First(() -> events.add("first")))
                .eager(
                        Second.class,
                        args -> new Second(() -> {
                            throw shared;
                        }))
                .eager(
                        Third.class,
                        args -> new Third(() -> {
                            throw shared;
                        })));
        assertTrue(loader.load());
        assertSame(shared, assertThrows(IllegalStateException.class, loader::unload));
        assertEquals(List.of("first"), events);
        assertEquals(0, shared.getSuppressed().length);
        assertNull(loader.get(First.class));
        loader.unload();
    }

    @Test
    void repeatedExceptionDoesNotInterruptRemainingModuleCleanup() {
        RuntimeException shared = new IllegalStateException("shared");
        List<String> events = new ArrayList<>();
        WeftLoader loader = new WeftLoader(new TestRegistry()
                .eager(First.class, args -> new First(() -> events.add("first")))
                .module(First.class)
                .eager(
                        Second.class,
                        args -> new Second(() -> {
                            throw shared;
                        }))
                .module(Second.class)
                .eager(
                        Third.class,
                        args -> new Third(() -> {
                            throw shared;
                        }))
                .module(Third.class));
        assertTrue(loader.load());
        assertSame(shared, assertThrows(IllegalStateException.class, () -> Modules.deactivate(loader)));
        assertEquals(List.of("first"), events);
        assertEquals(0, shared.getSuppressed().length);
    }

    private static class First implements Loader, ModuleLifecycle {
        final Runnable cleanup;

        First(Runnable cleanup) {
            this.cleanup = cleanup;
        }

        @Override
        public boolean load() {
            return true;
        }

        @Override
        public boolean activate() {
            return true;
        }

        @Override
        public void unload() {
            cleanup.run();
        }

        @Override
        public void deactivate() {
            cleanup.run();
        }
    }

    private static final class Second extends First {
        Second(Runnable cleanup) {
            super(cleanup);
        }
    }

    private static final class Third extends First {
        Third(Runnable cleanup) {
            super(cleanup);
        }
    }
}
