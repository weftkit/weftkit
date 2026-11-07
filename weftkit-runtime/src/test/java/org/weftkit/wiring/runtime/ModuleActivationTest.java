package org.weftkit.wiring.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.weftkit.wiring.ModuleLifecycle;
import org.weftkit.wiring.registry.TestRegistry;

class ModuleActivationTest {
    private final List<String> events = new ArrayList<>();
    private BooleanSupplier activate = () -> true;
    private Runnable deactivate = () -> {};
    private final WeftLoader loader = new WeftLoader(new TestRegistry()
            .eager(First.class, args -> new First())
            .module(First.class)
            .eager(Second.class, args -> new Second())
            .module(Second.class));

    @Test
    void repeatedActivationAndDeactivationRunEachHookOnce() {
        assertTrue(loader.load());
        Modules.deactivate(loader);
        assertTrue(events.isEmpty());
        assertTrue(Modules.activate(loader));
        assertTrue(Modules.activate(loader));
        Modules.deactivate(loader);
        Modules.deactivate(loader);
        assertEquals(List.of("+first", "+second", "-second", "-first"), events);
        assertThrows(IllegalStateException.class, () -> Modules.activate(loader));
        loader.unload();
        assertEquals(4, events.size());
    }

    @Test
    void declinedActivationRollsBackOnceAndRequiresReload() {
        activate = () -> false;
        assertTrue(loader.load());
        assertFalse(Modules.activate(loader));
        Modules.deactivate(loader);
        assertThrows(IllegalStateException.class, () -> Modules.activate(loader));
        assertEquals(List.of("+first", "+second", "-second", "-first"), events);
        loader.unload();
        activate = () -> true;
        assertTrue(loader.load());
        assertTrue(Modules.activate(loader));
        loader.unload();
        assertEquals(8, events.size());
    }

    @Test
    void activationAndRollbackFailuresDoNotPermitAnotherAttempt() {
        AssertionError original = new AssertionError("activate");
        RuntimeException rollback = new IllegalStateException("rollback");
        activate = () -> {
            throw original;
        };
        deactivate = () -> {
            throw rollback;
        };
        assertTrue(loader.load());
        assertSame(original, assertThrows(AssertionError.class, () -> Modules.activate(loader)));
        assertArrayEquals(new Throwable[] {rollback}, original.getSuppressed());
        assertEquals(List.of("+first", "+second", "-second", "-first"), events);
        assertThrows(IllegalStateException.class, () -> Modules.activate(loader));
        Modules.deactivate(loader);
        loader.unload();
        assertEquals(4, events.size());
    }

    @Test
    void failedDeactivationIsNotRepeatedAndUnloadStillClearsTheGraph() {
        RuntimeException failure = new IllegalStateException("deactivate");
        deactivate = () -> {
            throw failure;
        };
        assertTrue(loader.load());
        assertTrue(Modules.activate(loader));
        assertSame(failure, assertThrows(IllegalStateException.class, loader::unload));
        assertNull(loader.get(First.class));
        loader.unload();
        Modules.deactivate(loader);
        assertEquals(List.of("+first", "+second", "-second", "-first"), events);
    }

    @Test
    void activationRequiresALoadedGraph() {
        assertThrows(IllegalStateException.class, () -> Modules.activate(loader));
        assertTrue(events.isEmpty());
    }

    @Test
    void nestedLifecycleChangesAreRejectedWithoutRepeatingHooks() {
        activate = () -> {
            assertThrows(IllegalStateException.class, () -> Modules.activate(loader));
            assertThrows(IllegalStateException.class, () -> Modules.deactivate(loader));
            assertThrows(IllegalStateException.class, loader::unload);
            return true;
        };
        deactivate = () -> {
            assertThrows(IllegalStateException.class, () -> Modules.activate(loader));
            assertThrows(IllegalStateException.class, () -> Modules.deactivate(loader));
            assertThrows(IllegalStateException.class, loader::unload);
        };
        assertTrue(loader.load());
        assertTrue(Modules.activate(loader));
        loader.unload();
        assertEquals(List.of("+first", "+second", "-second", "-first"), events);
    }

    @Test
    void unloadWaitsForActivationBeforeDeactivatingModules() throws InterruptedException {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        activate = () -> {
            entered.countDown();
            try {
                assertTrue(release.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException ex) {
                throw new AssertionError(ex);
            }
            return true;
        };
        assertTrue(loader.load());
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch unloadStarted = new CountDownLatch(1);
        Thread activator = new Thread(() -> {
            try {
                Modules.activate(loader);
            } catch (Throwable ex) {
                failure.set(ex);
            }
        });
        Thread unloader = new Thread(() -> {
            unloadStarted.countDown();
            try {
                loader.unload();
            } catch (Throwable ex) {
                failure.set(ex);
            }
        });
        activator.start();
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            unloader.start();
            assertTrue(unloadStarted.await(5, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            activator.join(5000);
            unloader.join(5000);
        }
        assertFalse(activator.isAlive());
        assertFalse(unloader.isAlive());
        assertNull(failure.get());
        assertEquals(List.of("+first", "+second", "-second", "-first"), events);
    }

    private final class First implements ModuleLifecycle {
        @Override
        public boolean activate() {
            events.add("+first");
            return true;
        }

        @Override
        public void deactivate() {
            events.add("-first");
        }
    }

    private final class Second implements ModuleLifecycle {
        @Override
        public boolean activate() {
            events.add("+second");
            return activate.getAsBoolean();
        }

        @Override
        public void deactivate() {
            events.add("-second");
            deactivate.run();
        }
    }
}
