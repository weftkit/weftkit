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
        assertTrue(Modules.activate(loader));
        assertSame(shared, assertThrows(IllegalStateException.class, () -> Modules.deactivate(loader)));
        assertEquals(List.of("first"), events);
        assertEquals(0, shared.getSuppressed().length);
    }

    @Test
    void loadErrorRollsBackTheFailingComponentAndEarlierComponents() {
        AssertionError original = new AssertionError("load");
        LinkageError cleanup = new LinkageError("cleanup");
        List<String> events = new ArrayList<>();
        WeftLoader loader = new WeftLoader(new TestRegistry()
                .eager(First.class, args -> new First(() -> events.add("first")))
                .eager(
                        Second.class,
                        args ->
                                new Second(() -> {
                                    throw cleanup;
                                }) {
                                    @Override
                                    public boolean load() {
                                        throw original;
                                    }
                                }));
        assertSame(original, assertThrows(AssertionError.class, loader::load));
        assertArrayEquals(new Throwable[] {cleanup}, original.getSuppressed());
        assertEquals(List.of("first"), events);
        assertNull(loader.get(First.class));
        loader.unload();
    }

    @Test
    void constructorErrorRollsBackEarlierComponentsWithoutWrapping() {
        LinkageError original = new LinkageError("constructor");
        List<String> events = new ArrayList<>();
        WeftLoader loader = new WeftLoader(new TestRegistry()
                .eager(First.class, args -> new First(() -> events.add("first")))
                .eager(Second.class, args -> {
                    throw original;
                }));
        assertSame(original, assertThrows(LinkageError.class, loader::load));
        assertEquals(List.of("first"), events);
        assertNull(loader.get(First.class));
    }

    @Test
    void teardownErrorDoesNotPreventRemainingCleanupAndReset() {
        AssertionError original = new AssertionError("unload");
        List<String> events = new ArrayList<>();
        WeftLoader loader = new WeftLoader(new TestRegistry()
                .eager(First.class, args -> new First(() -> events.add("first")))
                .eager(
                        Second.class,
                        args -> new Second(() -> {
                            throw original;
                        })));
        assertTrue(loader.load());
        assertSame(original, assertThrows(AssertionError.class, loader::unload));
        assertEquals(List.of("first"), events);
        assertNull(loader.get(First.class));
        loader.unload();
    }

    @Test
    void moduleActivationErrorRollsBackAttemptedModules() {
        AssertionError original = new AssertionError("activate");
        LinkageError cleanup = new LinkageError("deactivate");
        List<String> events = new ArrayList<>();
        WeftLoader loader = new WeftLoader(new TestRegistry()
                .eager(First.class, args -> new First(() -> events.add("first")))
                .module(First.class)
                .eager(
                        Second.class,
                        args ->
                                new Second(() -> {
                                    throw cleanup;
                                }) {
                                    @Override
                                    public boolean activate() {
                                        throw original;
                                    }
                                })
                .module(Second.class));
        assertTrue(loader.load());
        assertSame(original, assertThrows(AssertionError.class, () -> Modules.activate(loader)));
        assertArrayEquals(new Throwable[] {cleanup}, original.getSuppressed());
        assertEquals(List.of("first"), events);
    }

    @Test
    void lazyErrorIsStickyUntilResetAndReleasesPartialResources() {
        AssertionError original = new AssertionError("lazy load");
        List<String> events = new ArrayList<>();
        WeftLoader loader =
                new WeftLoader(new TestRegistry().lazy(First.class, args -> new First(() -> events.add("cleanup")) {
                    @Override
                    public boolean load() {
                        throw original;
                    }
                }));
        assertTrue(loader.load());
        assertSame(original, assertThrows(AssertionError.class, () -> loader.create(First.class)));
        assertSame(
                original,
                assertThrows(
                                org.weftkit.wiring.runtime.exception.ComponentLoadException.class,
                                () -> loader.create(First.class))
                        .getCause());
        assertEquals(List.of("cleanup"), events);
        assertTrue(loader.resetFailure(First.class));
        assertSame(original, assertThrows(AssertionError.class, () -> loader.create(First.class)));
        assertEquals(List.of("cleanup", "cleanup"), events);
        loader.unload();
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

    private static class Second extends First {
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
