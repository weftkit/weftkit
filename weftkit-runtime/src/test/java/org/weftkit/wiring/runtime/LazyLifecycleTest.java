package org.weftkit.wiring.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.weftkit.wiring.Loader;

// Hand-built registries keep the eager to lazy edges out of the shared generated sample fixture
class LazyLifecycleTest {

    private static final class Events {

        final List<String> log = new ArrayList<>();

        boolean eagerProceed = true;

        boolean lazyProceed = true;

        boolean eagerThrowOnLoad;

        boolean lazyUnloadFailure;

        int holderValue;

        int readValue;

        Token seenToken;
    }

    public static final class Token {}

    public static final class LazyCore implements Loader {

        private final Events events;

        private final Token token = new Token();

        LazyCore(Events events) {
            this.events = events;
        }

        @Override
        public boolean load() {
            events.log.add("lazy load");
            return events.lazyProceed;
        }

        @Override
        public void unload() {
            events.log.add("lazy unload");
            if (events.lazyUnloadFailure) throw new IllegalStateException("lazy teardown");
        }

        public Token token() {
            return token;
        }
    }

    public static final class Alpha implements Loader {

        private final Events events;

        Alpha(Events events) {
            this.events = events;
        }

        @Override
        public boolean load() {
            events.log.add("alpha load");
            return true;
        }

        @Override
        public void unload() {
            events.log.add("alpha unload");
        }
    }

    public static final class EagerUser implements Loader {

        private final Events events;

        EagerUser(Events events, LazyCore core) {
            this.events = events;
        }

        @Override
        public boolean load() {
            events.log.add("eager load");
            if (events.eagerThrowOnLoad) throw new IllegalStateException("eager failure");
            return events.eagerProceed;
        }

        @Override
        public void unload() {
            events.log.add("eager unload");
        }
    }

    public static final class ProductUser {

        ProductUser(Events events, Token token) {
            events.seenToken = token;
        }
    }

    public static final class LazyIniter implements Loader {

        private final Events events;

        LazyIniter(Events events) {
            this.events = events;
        }

        @Override
        public boolean load() {
            events.log.add("init");
            events.holderValue = 7;
            return true;
        }
    }

    public static final class Reader {

        Reader(Events events) {
            events.log.add("reader");
            events.readValue = events.holderValue;
        }
    }

    private static ComponentRegistry graphRegistry(Events events) {
        return new ComponentRegistry() {

            @Override
            public List<Class<?>> loadOrder() {
                return List.of(Alpha.class, EagerUser.class);
            }

            @Override
            public Set<Class<?>> lazySingletons() {
                return Set.of(LazyCore.class);
            }

            @Override
            public Map<Class<?>, List<Dependency>> parameters() {
                return Map.of(
                        Alpha.class, List.of(),
                        LazyCore.class, List.of(),
                        EagerUser.class, List.of(new Dependency(LazyCore.class, "", false)));
            }

            @Override
            public Map<Class<?>, Function<Object[], Object>> factories() {
                return Map.of(
                        Alpha.class, arguments -> new Alpha(events),
                        LazyCore.class, arguments -> new LazyCore(events),
                        EagerUser.class, arguments -> new EagerUser(events, (LazyCore) arguments[0]));
            }

            @Override
            public Map<Class<?>, Map<String, Class<?>>> productOwners() {
                return Map.of();
            }

            @Override
            public Map<Class<?>, Map<String, Function<Object, Object>>> productGetters() {
                return Map.of();
            }

            @Override
            public Map<Class<?>, Map<String, Class<?>>> bindings() {
                return Map.of();
            }
        };
    }

    private static ComponentRegistry productRegistry(Events events) {
        return new ComponentRegistry() {

            @Override
            public List<Class<?>> loadOrder() {
                return List.of(ProductUser.class);
            }

            @Override
            public Set<Class<?>> lazySingletons() {
                return Set.of(LazyCore.class);
            }

            @Override
            public Map<Class<?>, List<Dependency>> parameters() {
                return Map.of(
                        LazyCore.class, List.of(),
                        ProductUser.class, List.of(new Dependency(Token.class, "", false)));
            }

            @Override
            public Map<Class<?>, Function<Object[], Object>> factories() {
                return Map.of(
                        LazyCore.class, arguments -> new LazyCore(events),
                        ProductUser.class, arguments -> new ProductUser(events, (Token) arguments[0]));
            }

            @Override
            public Map<Class<?>, Map<String, Class<?>>> productOwners() {
                return Map.of(Token.class, Map.of("", LazyCore.class));
            }

            @Override
            public Map<Class<?>, Map<String, Function<Object, Object>>> productGetters() {
                return Map.of(Token.class, Map.of("", owner -> ((LazyCore) owner).token()));
            }

            @Override
            public Map<Class<?>, Map<String, Class<?>>> bindings() {
                return Map.of();
            }
        };
    }

    private static ComponentRegistry requirementRegistry(Events events) {
        return new ComponentRegistry() {

            @Override
            public List<Class<?>> loadOrder() {
                return List.of();
            }

            @Override
            public Set<Class<?>> lazySingletons() {
                return Set.of(LazyIniter.class);
            }

            @Override
            public Map<Class<?>, List<Class<?>>> requirements() {
                return Map.of(Reader.class, List.of(LazyIniter.class));
            }

            @Override
            public Map<Class<?>, List<Dependency>> parameters() {
                return Map.of(LazyIniter.class, List.of(), Reader.class, List.of());
            }

            @Override
            public Map<Class<?>, Function<Object[], Object>> factories() {
                return Map.of(
                        LazyIniter.class, arguments -> new LazyIniter(events),
                        Reader.class, arguments -> new Reader(events));
            }

            @Override
            public Map<Class<?>, Map<String, Class<?>>> productOwners() {
                return Map.of();
            }

            @Override
            public Map<Class<?>, Map<String, Function<Object, Object>>> productGetters() {
                return Map.of();
            }

            @Override
            public Map<Class<?>, Map<String, Class<?>>> bindings() {
                return Map.of();
            }
        };
    }

    @Test
    void eagerConstructorMaterializesLazyDependencyDuringLoadAndUnloadsItLast() {
        Events events = new Events();
        WeftLoader loader = new WeftLoader(graphRegistry(events));
        assertTrue(loader.load());
        assertEquals(List.of("alpha load", "lazy load", "eager load"), events.log);
        loader.unload();
        assertEquals(
                List.of("alpha load", "lazy load", "eager load", "eager unload", "lazy unload", "alpha unload"),
                events.log);
    }

    @Test
    void eagerAbortUnloadsMaterializedLazySingletons() {
        Events events = new Events();
        events.eagerProceed = false;
        WeftLoader loader = new WeftLoader(graphRegistry(events));
        assertFalse(loader.load());
        assertTrue(events.log.contains("lazy unload"));
        assertFalse(events.log.contains("eager unload"));
        assertNull(loader.get(LazyCore.class));
    }

    @Test
    void eagerLoadFailureUnloadsMaterializedLazyWithTeardownFailureSuppressed() {
        Events events = new Events();
        events.eagerThrowOnLoad = true;
        events.lazyUnloadFailure = true;
        WeftLoader loader = new WeftLoader(graphRegistry(events));
        IllegalStateException ex = assertThrows(IllegalStateException.class, loader::load);
        assertEquals("eager failure", ex.getMessage());
        assertEquals(1, ex.getSuppressed().length);
        assertEquals("lazy teardown", ex.getSuppressed()[0].getMessage());
        assertTrue(events.log.contains("alpha unload"));
    }

    @Test
    void lazyFailureDuringEagerLoadTriggersFullTeardown() {
        Events events = new Events();
        events.lazyProceed = false;
        WeftLoader loader = new WeftLoader(graphRegistry(events));
        ComponentLoadException ex = assertThrows(ComponentLoadException.class, loader::load);
        assertTrue(ex.getMessage().contains("aborted"));
        // The lazy hook never completed, so only alpha is torn down
        assertEquals(List.of("alpha load", "lazy load", "alpha unload"), events.log);
        assertNull(loader.get(LazyCore.class));
        assertNull(loader.get(Alpha.class));
    }

    @Test
    void eagerComponentConsumesProductOfLazyOwnerDuringLoad() {
        Events events = new Events();
        WeftLoader loader = new WeftLoader(productRegistry(events));
        assertTrue(loader.load());
        assertEquals(List.of("lazy load"), events.log);
        LazyCore core = loader.get(LazyCore.class);
        assertNotNull(core);
        assertSame(core.token(), events.seenToken);
    }

    @Test
    void requirementsMaterializeLazyInitializerBeforeTheDependentBuilds() {
        Events events = new Events();
        WeftLoader loader = new WeftLoader(requirementRegistry(events));
        loader.create(Reader.class);
        assertEquals(List.of("init", "reader"), events.log);
        assertEquals(7, events.readValue);
        loader.create(Reader.class);
        // The initializer hook ran only once across repeated creates
        assertEquals(List.of("init", "reader", "reader"), events.log);
    }
}
