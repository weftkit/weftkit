package org.weftkit.wiring.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.weftkit.wiring.registry.TestRegistry.on;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.weftkit.wiring.Loader;
import org.weftkit.wiring.registry.TestRegistry;

class ShutdownConcurrencyTest {
    @Test
    void admittedPrototypeMayFinishAfterUnloadWithoutRevivingSingletons() throws InterruptedException {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Resource resource = new Resource();
        WeftLoader loader = new WeftLoader(new TestRegistry()
                .eager(Resource.class, args -> resource)
                .plain(
                        Worker.class,
                        args -> {
                            entered.countDown();
                            await(release);
                            return new Worker((Resource) args[0]);
                        },
                        on(Resource.class)));
        assertTrue(loader.load());
        AtomicReference<Worker> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                result.set(loader.create(Worker.class));
            } catch (Throwable ex) {
                failure.set(ex);
            }
        });
        worker.start();
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            loader.unload();
            assertTrue(resource.unloaded);
            assertNull(result.get());
        } finally {
            release.countDown();
            worker.join(5000);
        }
        assertFalse(worker.isAlive());
        assertNull(failure.get());
        assertSame(resource, result.get().resource());
        assertNull(loader.get(Resource.class));
        assertRejectedOnOtherThread(() -> loader.create(Worker.class));
    }

    @Test
    void admittedCacheReadMayObserveTheClearedCacheAfterUnload() throws InterruptedException {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        WeftLoader loader = new WeftLoader(
                new TestRegistry().eager(Resource.class, args -> new Resource()).bindings(() -> {
                    if (Thread.currentThread().getName().equals("cache-reader")) {
                        entered.countDown();
                        await(release);
                    }
                    return Map.of();
                }));
        assertTrue(loader.load());
        Resource retained = loader.get(Resource.class);
        AtomicReference<Resource> result = new AtomicReference<>(retained);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread reader = new Thread(
                () -> {
                    try {
                        result.set(loader.get(Resource.class));
                    } catch (Throwable ex) {
                        failure.set(ex);
                    }
                },
                "cache-reader");
        reader.start();
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            loader.unload();
            assertTrue(retained.unloaded);
        } finally {
            release.countDown();
            reader.join(5000);
        }
        assertFalse(reader.isAlive());
        assertNull(failure.get());
        assertNull(result.get());
        assertRejectedOnOtherThread(() -> loader.get(Resource.class));
    }

    private static void assertRejectedOnOtherThread(Runnable operation) throws InterruptedException {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                operation.run();
            } catch (Throwable ex) {
                failure.set(ex);
            }
        });
        worker.start();
        worker.join(5000);
        assertFalse(worker.isAlive());
        assertInstanceOf(IllegalStateException.class, failure.get());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("Timed out waiting for test release");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError(ex);
        }
    }

    private static final class Resource implements Loader {
        volatile boolean unloaded;

        @Override
        public boolean load() {
            return true;
        }

        @Override
        public void unload() {
            unloaded = true;
        }
    }

    private record Worker(Resource resource) {}
}
