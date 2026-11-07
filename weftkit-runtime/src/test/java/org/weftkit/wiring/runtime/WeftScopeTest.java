package org.weftkit.wiring.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.weftkit.wiring.registry.TestRegistry.on;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.weftkit.wiring.Loader;
import org.weftkit.wiring.registry.TestRegistry;
import org.weftkit.wiring.runtime.exception.ComponentLoadException;

class WeftScopeTest {
    private final List<String> events = new ArrayList<>();

    class Owner implements Loader {
        private final WeftScope scope;
        private final boolean accepts;

        Owner(WeftScope scope, boolean accepts) {
            this.scope = scope;
            this.accepts = accepts;
            scope.own(() -> events.add("first"));
            scope.own(() -> events.add("second"));
        }

        @Override
        public boolean load() {
            return accepts;
        }

        @Override
        public void unload() {
            events.add("unload");
            assertThrows(IllegalStateException.class, () -> scope.own(() -> {}));
        }
    }

    @Test
    void closesInReverseOrderBeforeUnloadAndUsesAFreshScopeAfterReload() {
        WeftLoader loader = new WeftLoader(new TestRegistry()
                .eager(Owner.class, args -> new Owner((WeftScope) args[0], true), on(WeftScope.class)));
        assertTrue(loader.load());
        WeftScope first = loader.get(Owner.class).scope;
        loader.unload();
        loader.unload();
        assertEquals(List.of("second", "first", "unload"), events);
        assertTrue(loader.load());
        assertNotSame(first, loader.get(Owner.class).scope);
        loader.unload();
        assertEquals(6, events.size());
    }

    @Test
    void declinedLoadClosesResourcesBeforeItsUnloadHook() {
        WeftLoader loader = new WeftLoader(new TestRegistry()
                .eager(Owner.class, args -> new Owner((WeftScope) args[0], false), on(WeftScope.class)));
        assertFalse(loader.load());
        assertEquals(List.of("second", "first", "unload"), events);
    }

    @Test
    void constructorFailureClosesResourcesAndPreservesTheOriginalFailure() {
        IllegalStateException original = new IllegalStateException("constructor");
        IllegalStateException cleanup = new IllegalStateException("cleanup");
        WeftLoader loader = new WeftLoader(new TestRegistry()
                .eager(
                        Owner.class,
                        args -> {
                            WeftScope scope = (WeftScope) args[0];
                            scope.own(() -> events.add("closed"));
                            scope.own(() -> {
                                throw cleanup;
                            });
                            throw original;
                        },
                        on(WeftScope.class)));
        ComponentLoadException failure = assertThrows(ComponentLoadException.class, loader::load);
        assertSame(original, failure.getCause());
        assertArrayEquals(new Throwable[] {cleanup}, failure.getSuppressed());
        assertEquals(List.of("closed"), events);
    }

    @Test
    void lazyFailureClosesOnlyItsOwnResourcesAndResetCreatesANewScope() {
        List<WeftScope> scopes = new ArrayList<>();
        WeftLoader loader = new WeftLoader(new TestRegistry()
                .eager(
                        String.class,
                        args -> {
                            ((WeftScope) args[0]).own(() -> events.add("peer"));
                            return "peer";
                        },
                        on(WeftScope.class))
                .lazy(
                        Owner.class,
                        args -> {
                            WeftScope scope = (WeftScope) args[0];
                            scopes.add(scope);
                            return new Owner(scope, false);
                        },
                        on(WeftScope.class)));
        assertTrue(loader.load());
        assertThrows(ComponentLoadException.class, () -> loader.create(Owner.class));
        assertEquals(List.of("second", "first", "unload"), events);
        assertThrows(ComponentLoadException.class, () -> loader.create(Owner.class));
        assertEquals(1, scopes.size());
        assertTrue(loader.resetFailure(Owner.class));
        assertThrows(ComponentLoadException.class, () -> loader.create(Owner.class));
        assertNotSame(scopes.get(0), scopes.get(1));
        loader.unload();
        assertEquals("peer", events.get(events.size() - 1));
    }

    @Test
    void cleanupFailureDoesNotSkipTheOwnersUnloadHookOrOtherResources() {
        IllegalStateException cleanup = new IllegalStateException("cleanup");
        WeftLoader loader = new WeftLoader(new TestRegistry()
                .eager(
                        Owner.class,
                        args -> {
                            WeftScope scope = (WeftScope) args[0];
                            Owner owner = new Owner(scope, true);
                            scope.own(() -> {
                                throw cleanup;
                            });
                            return owner;
                        },
                        on(WeftScope.class)));
        assertTrue(loader.load());
        assertSame(cleanup, assertThrows(IllegalStateException.class, loader::unload));
        assertEquals(List.of("second", "first", "unload"), events);
        assertNull(loader.get(Owner.class));
    }

    @Test
    void scopesCloseResourcesOfSingletonsWithoutLoaderHooks() {
        WeftLoader loader = new WeftLoader(new TestRegistry()
                .eager(
                        String.class,
                        args -> {
                            ((WeftScope) args[0]).own(() -> events.add("closed"));
                            return "value";
                        },
                        on(WeftScope.class)));
        assertTrue(loader.load());
        loader.unload();
        assertEquals(List.of("closed"), events);
    }

    @Test
    void resourcesOfSingletonsMaterializedDuringUnloadStillClose() {
        WeftLoader[] graph = new WeftLoader[1];
        graph[0] = new WeftLoader(new TestRegistry()
                .eager(Loader.class, args -> new Loader() {
                    @Override
                    public boolean load() {
                        return true;
                    }

                    @Override
                    public void unload() {
                        graph[0].create(String.class);
                    }
                })
                .lazy(
                        String.class,
                        args -> {
                            ((WeftScope) args[0]).own(() -> events.add("late"));
                            return "late";
                        },
                        on(WeftScope.class)));
        assertTrue(graph[0].load());
        graph[0].unload();
        assertEquals(List.of("late"), events);
        assertNull(graph[0].get(String.class));
    }
}
