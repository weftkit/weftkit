package org.weftkit.wiring.runtime;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.weftkit.wiring.runtime.registry.ComponentRegistry;
import org.weftkit.wiring.runtime.registry.Dependency;

// Hand-built registry, the thread confinement contract needs no generated wiring
class ThreadConfinementTest {

    public static final class Core {}

    public static final class Worker {
        Worker(Core core) {}
    }

    private static ComponentRegistry registry() {
        return new ComponentRegistry() {

            @Override
            public List<Class<?>> loadOrder() {
                return List.of(Core.class);
            }

            @Override
            public Set<Class<?>> lazySingletons() {
                return Set.of();
            }

            @Override
            public Map<Class<?>, List<Dependency>> parameters() {
                return Map.of(
                        Core.class, List.of(),
                        Worker.class, List.of(new Dependency(Core.class, "", false)));
            }

            @Override
            public Map<Class<?>, Function<Object[], Object>> factories() {
                return Map.of(
                        Core.class, arguments -> new Core(),
                        Worker.class, arguments -> new Worker((Core) arguments[0]));
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

    private static Throwable onOtherThread(Runnable action) throws InterruptedException {
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread thread = new Thread(
                () -> {
                    try {
                        action.run();
                    } catch (Throwable ex) {
                        thrown.set(ex);
                    }
                },
                "async-task");
        thread.start();
        thread.join();
        return thrown.get();
    }

    @Test
    void mutatingCallsOffTheOwnerThreadFailFast() throws InterruptedException {
        WeftLoader loader = new WeftLoader(registry());
        loader.load();
        for (Runnable call : List.<Runnable>of(
                loader::load,
                () -> loader.create(Worker.class),
                () -> loader.createAll(Worker.class),
                () -> loader.get(Core.class),
                loader::loadTimings,
                loader::totalLoadTime,
                loader::unload)) {
            Throwable thrown = onOtherThread(call);
            assertInstanceOf(IllegalStateException.class, thrown);
            assertTrue(thrown.getMessage().contains("async-task"));
        }
        // The graph stayed intact and the owner thread keeps full access
        assertNotNull(loader.get(Core.class));
        assertNotNull(loader.create(Worker.class));
        loader.unload();
    }

    @Test
    void immutableRegistryViewsStayAccessibleFromAnyThread() throws InterruptedException {
        WeftLoader loader = new WeftLoader(registry());
        loader.load();
        assertNull(onOtherThread(() -> {
            assertTrue(loader.loadOrder().contains(Core.class));
            assertNull(loader.ambient(String.class));
            assertNotNull(loader.registry());
        }));
        loader.unload();
    }

    @Test
    void loaderOwnedByABackgroundThreadRejectsTheTestThread() throws InterruptedException {
        AtomicReference<WeftLoader> built = new AtomicReference<>();
        Throwable background = onOtherThread(() -> {
            WeftLoader loader = new WeftLoader(registry());
            assertTrue(loader.load());
            assertNotNull(loader.create(Worker.class));
            built.set(loader);
        });
        assertNull(background);
        IllegalStateException thrown =
                assertThrows(IllegalStateException.class, () -> built.get().create(Worker.class));
        assertTrue(thrown.getMessage().contains("async-task"));
        assertTrue(thrown.getMessage().contains(Thread.currentThread().getName()));
    }
}
