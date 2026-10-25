package org.weftkit.wiring.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.weftkit.wiring.Loader;
import org.weftkit.wiring.registry.ComponentRegistry;
import org.weftkit.wiring.registry.Dependency;
import org.weftkit.wiring.runtime.exception.ComponentLoadException;

// Hand-built registry, the thread confinement contract needs no generated wiring
class ThreadConfinementTest {

    private static final class Events {

        final AtomicReference<WeftLoader> loader = new AtomicReference<>();

        // Run on a fresh thread from Core's load hook
        volatile Runnable duringLoad;

        volatile Throwable duringLoadFailure;

        // Set: the Cache constructor, or its load hook with cacheParksInLoad, blocks until
        // cacheRelease
        volatile CountDownLatch cacheEntered;

        volatile CountDownLatch cacheRelease;

        volatile boolean cacheParksInLoad;

        volatile boolean cacheFails;

        final AtomicInteger cacheConstructions = new AtomicInteger();

        final AtomicInteger cacheUnloads = new AtomicInteger();
    }

    public interface Service {}

    public static final class Core implements Loader, Service {

        private final Events events;

        Core(Events events) {
            this.events = events;
        }

        @Override
        public boolean load() {
            if (events.duringLoad == null) return true;
            Thread probe = new Thread(
                    () -> {
                        try {
                            events.duringLoad.run();
                        } catch (Throwable ex) {
                            events.duringLoadFailure = ex;
                        }
                    },
                    "async-task");
            probe.start();
            try {
                probe.join();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return true;
        }
    }

    public static final class Worker {
        Worker(Core core) {}
    }

    public static final class Cache implements Loader, Service {

        private final Events events;

        Cache(Events events) {
            this.events = events;
            events.cacheConstructions.incrementAndGet();
            if (events.cacheFails) throw new IllegalStateException("cache failure");
            if (!events.cacheParksInLoad) park();
        }

        @Override
        public boolean load() {
            if (events.cacheParksInLoad) park();
            return true;
        }

        private void park() {
            if (events.cacheEntered == null) return;
            events.cacheEntered.countDown();
            try {
                assertTrue(events.cacheRelease.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void unload() {
            events.cacheUnloads.incrementAndGet();
        }
    }

    private static ComponentRegistry registry(Events events) {
        return new ComponentRegistry() {

            @Override
            public List<Class<?>> loadOrder() {
                return List.of(Core.class);
            }

            @Override
            public Set<Class<?>> lazySingletons() {
                return Set.of(Cache.class);
            }

            @Override
            public Map<Class<?>, List<Dependency>> parameters() {
                return Map.of(
                        Core.class, List.of(),
                        Cache.class, List.of(),
                        Worker.class, List.of(new Dependency(Core.class, "", false)));
            }

            @Override
            public Map<Class<?>, Function<Object[], Object>> factories() {
                return Map.of(
                        Core.class, arguments -> new Core(events),
                        Cache.class, arguments -> new Cache(events),
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

    private static WeftLoader loaded(Events events) {
        WeftLoader loader = new WeftLoader(registry(events));
        events.loader.set(loader);
        assertTrue(loader.load());
        return loader;
    }

    private static Thread start(String name, Runnable action, AtomicReference<Throwable> thrown) {
        Thread thread = new Thread(
                () -> {
                    try {
                        action.run();
                    } catch (Throwable ex) {
                        thrown.set(ex);
                    }
                },
                name);
        thread.start();
        return thread;
    }

    private static Throwable onOtherThread(Runnable action) throws InterruptedException {
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        start("async-task", action, thrown).join();
        return thrown.get();
    }

    private static void awaitParked(Thread thread) throws InterruptedException {
        awaitParked(thread, frame -> true);
    }

    private static void awaitParkedIn(Thread thread, String method) throws InterruptedException {
        awaitParked(thread, frame -> frame.getMethodName().equals(method));
    }

    private static void awaitParked(Thread thread, Predicate<StackTraceElement> frame) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (true) {
            Thread.State state = thread.getState();
            boolean parked = state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING;
            if (parked && Stream.of(thread.getStackTrace()).anyMatch(frame)) return;
            assertTrue(System.nanoTime() < deadline, "thread never parked");
            Thread.sleep(1);
        }
    }

    @Test
    void lifecycleCallsOffTheOwnerThreadFailFast() throws InterruptedException {
        WeftLoader loader = loaded(new Events());
        for (Runnable call : List.<Runnable>of(loader::load, () -> loader.resetFailure(Cache.class), loader::unload)) {
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
    void loadedGraphIsSharedWithOtherThreads() throws InterruptedException {
        WeftLoader loader = loaded(new Events());
        Core core = loader.get(Core.class);
        List<Service> services = loader.createAll(Service.class);
        Map<Class<?>, java.time.Duration> timings = loader.loadTimings();
        assertNull(onOtherThread(() -> {
            assertSame(core, loader.get(Core.class));
            assertSame(core, loader.create(Core.class));
            assertEquals(services, loader.createAll(Service.class));
            assertEquals(timings, loader.loadTimings());
            assertEquals(loader.totalLoadTime(), loader.totalLoadTime());
            assertTrue(loader.types(Service.class).contains(Cache.class));
            assertTrue(loader.componentEnabled(Worker.class));
            assertNotNull(loader.create(Worker.class));
        }));
        loader.unload();
    }

    @Test
    void accessFromAnotherThreadWhileLoadingFailsFast() {
        Events events = new Events();
        events.duringLoad = () -> events.loader.get().get(Core.class);
        loaded(events);
        Throwable thrown = events.duringLoadFailure;
        assertInstanceOf(IllegalStateException.class, thrown);
        assertTrue(thrown.getMessage().contains("async-task"));
        assertTrue(thrown.getMessage().contains("still loading"));
        events.loader.get().unload();
    }

    @Test
    void unloadedGraphRejectsOtherThreads() throws InterruptedException {
        Events events = new Events();
        WeftLoader loader = new WeftLoader(registry(events));
        Throwable thrown = onOtherThread(() -> loader.get(Core.class));
        assertInstanceOf(IllegalStateException.class, thrown);
        assertTrue(thrown.getMessage().contains("not loaded"));
        assertTrue(loader.load());
        loader.unload();
        assertInstanceOf(IllegalStateException.class, onOtherThread(() -> loader.create(Worker.class)));
        assertNull(loader.get(Core.class));
    }

    @Test
    void concurrentMaterializationYieldsOneLazyInstance() throws InterruptedException {
        Events events = new Events();
        events.cacheEntered = new CountDownLatch(1);
        events.cacheRelease = new CountDownLatch(1);
        WeftLoader loader = loaded(events);
        AtomicReference<Object> first = new AtomicReference<>();
        AtomicReference<Object> second = new AtomicReference<>();
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread one = start("first", () -> first.set(loader.create(Cache.class)), thrown);
        assertTrue(events.cacheEntered.await(5, TimeUnit.SECONDS));
        Thread two = start("second", () -> second.set(loader.create(Cache.class)), thrown);
        // Second thread parks on the graph lock
        awaitParked(two);
        events.cacheRelease.countDown();
        one.join();
        two.join();
        assertNull(thrown.get());
        assertNotNull(first.get());
        assertSame(first.get(), second.get());
        assertEquals(1, events.cacheConstructions.get());
        assertEquals(
                1,
                loader.loadTimings().keySet().stream()
                        .filter(Cache.class::equals)
                        .count());
        loader.unload();
        assertEquals(1, events.cacheUnloads.get());
    }

    @Test
    void getHidesALazySingletonUntilItsLoadHookCompleted() throws InterruptedException {
        Events events = new Events();
        events.cacheParksInLoad = true;
        events.cacheEntered = new CountDownLatch(1);
        events.cacheRelease = new CountDownLatch(1);
        WeftLoader loader = loaded(events);
        AtomicReference<Object> first = new AtomicReference<>();
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread one = start("first", () -> first.set(loader.create(Cache.class)), thrown);
        assertTrue(events.cacheEntered.await(5, TimeUnit.SECONDS));
        assertNull(loader.get(Cache.class));
        assertNull(onOtherThread(() -> assertNull(loader.get(Cache.class))));
        events.cacheRelease.countDown();
        one.join();
        assertNull(thrown.get());
        assertSame(first.get(), loader.get(Cache.class));
        loader.unload();
    }

    @Test
    void plainComponentsBuildWhileALazyLoadHookRuns() throws InterruptedException {
        Events events = new Events();
        events.cacheParksInLoad = true;
        events.cacheEntered = new CountDownLatch(1);
        events.cacheRelease = new CountDownLatch(1);
        WeftLoader loader = loaded(events);
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread one = start("first", () -> loader.create(Cache.class), thrown);
        assertTrue(events.cacheEntered.await(5, TimeUnit.SECONDS));
        // Would park on the graph lock until the hook's latch times out if prototypes took it
        assertNotNull(loader.create(Worker.class));
        assertNull(onOtherThread(() -> assertNotNull(loader.create(Worker.class))));
        events.cacheRelease.countDown();
        one.join();
        assertNull(thrown.get());
        loader.unload();
    }

    @Test
    void unloadRacingAMaterializationNeverRevivesTheGraph() throws InterruptedException {
        Events events = new Events();
        events.cacheParksInLoad = true;
        events.cacheEntered = new CountDownLatch(1);
        events.cacheRelease = new CountDownLatch(1);
        AtomicReference<WeftLoader> built = new AtomicReference<>();
        AtomicReference<Object> first = new AtomicReference<>();
        AtomicReference<Object> second = new AtomicReference<>();
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        AtomicReference<Throwable> secondThrown = new AtomicReference<>();
        // The owner lives on a helper thread, so the test thread can time its unload
        CountDownLatch unloadNow = new CountDownLatch(1);
        Thread owner = start(
                "owner",
                () -> {
                    WeftLoader loader = new WeftLoader(registry(events));
                    assertTrue(loader.load());
                    built.set(loader);
                    try {
                        assertTrue(unloadNow.await(5, TimeUnit.SECONDS));
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                    loader.unload();
                    assertEquals(1, events.cacheUnloads.get());
                },
                thrown);
        awaitParked(owner);
        WeftLoader loader = built.get();
        Thread one = start("first", () -> first.set(loader.create(Cache.class)), thrown);
        assertTrue(events.cacheEntered.await(5, TimeUnit.SECONDS));
        // Passed the lock-free check while loaded, parks on the graph lock behind the hook
        Thread two = start("second", () -> second.set(loader.create(Cache.class)), secondThrown);
        awaitParked(two);
        unloadNow.countDown();
        awaitParkedIn(owner, "unload");
        events.cacheRelease.countDown();
        one.join();
        two.join();
        owner.join();
        assertNull(thrown.get());
        assertNotNull(first.get());
        // Either the second caller got the live instance before the unload or was turned away
        if (secondThrown.get() == null) assertSame(first.get(), second.get());
        else assertInstanceOf(IllegalStateException.class, secondThrown.get());
        assertEquals(1, events.cacheConstructions.get());
        assertEquals(1, events.cacheUnloads.get());
    }

    @Test
    void loadingALoadedGraphThrows() {
        WeftLoader loader = loaded(new Events());
        IllegalStateException thrown = assertThrows(IllegalStateException.class, loader::load);
        assertTrue(thrown.getMessage().contains("already loaded"));
        assertNotNull(loader.get(Core.class));
        loader.unload();
        assertTrue(loader.load());
        loader.unload();
    }

    @Test
    void timingsStartFreshOnReload() {
        Events events = new Events();
        WeftLoader loader = loaded(events);
        assertNotNull(loader.create(Cache.class));
        assertEquals(
                List.of(Core.class, Cache.class),
                List.copyOf(loader.loadTimings().keySet()));
        loader.unload();
        assertTrue(loader.loadTimings().isEmpty());
        assertTrue(loader.load());
        assertEquals(List.of(Core.class), List.copyOf(loader.loadTimings().keySet()));
        assertEquals(loader.loadTimings().get(Core.class), loader.totalLoadTime());
        loader.unload();
    }

    @Test
    void failedLazyMaterializationStaysFailedForOtherThreads() throws InterruptedException {
        Events events = new Events();
        events.cacheFails = true;
        WeftLoader loader = loaded(events);
        Throwable first = onOtherThread(() -> loader.create(Cache.class));
        assertInstanceOf(ComponentLoadException.class, first);
        Throwable second = onOtherThread(() -> loader.create(Cache.class));
        assertInstanceOf(ComponentLoadException.class, second);
        assertTrue(second.getMessage().contains("failed earlier"));
        assertSame(first, second.getCause());
        assertEquals(1, events.cacheConstructions.get());
        assertTrue(loader.resetFailure(Cache.class));
        loader.unload();
    }

    @Test
    void immutableRegistryViewsStayAccessibleFromAnyThread() throws InterruptedException {
        WeftLoader loader = loaded(new Events());
        assertNull(onOtherThread(() -> {
            assertTrue(loader.loadOrder().contains(Core.class));
            assertNull(loader.ambient(String.class));
            assertNotNull(loader.registry());
        }));
        loader.unload();
    }

    @Test
    void loaderOwnedByABackgroundThreadRejectsTheTestThreadWhileUnloaded() throws InterruptedException {
        AtomicReference<WeftLoader> built = new AtomicReference<>();
        Throwable background = onOtherThread(() -> {
            WeftLoader loader = new WeftLoader(registry(new Events()));
            assertTrue(loader.load());
            assertNotNull(loader.create(Worker.class));
            loader.unload();
            built.set(loader);
        });
        assertNull(background);
        IllegalStateException thrown =
                assertThrows(IllegalStateException.class, () -> built.get().create(Worker.class));
        assertTrue(thrown.getMessage().contains("async-task"));
        assertTrue(thrown.getMessage().contains(Thread.currentThread().getName()));
    }
}
