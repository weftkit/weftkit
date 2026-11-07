package org.weftkit.wiring.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.weftkit.wiring.registry.TestRegistry.on;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.weftkit.wiring.FeatureFlags;
import org.weftkit.wiring.Loader;
import org.weftkit.wiring.registry.Flag;
import org.weftkit.wiring.registry.TestRegistry;

class WeftDiagnosticsTest {
    record Consumer(WeftDiagnostics diagnostics) {}

    static class Lazy {}

    static class Broken {}

    @Test
    void inFlightFlagChecksCannotRestoreObservationsAfterUnload() throws Exception {
        CountDownLatch checking = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger checks = new AtomicInteger();
        WeftLoader loader = new WeftLoader(
                new TestRegistry().lazy(Lazy.class, args -> new Lazy()).flag(Lazy.class, new Flag("optional", false)),
                (FeatureFlags) key -> {
                    if (checks.incrementAndGet() > 1) {
                        checking.countDown();
                        try {
                            if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("Flag check timed out");
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError(ex);
                        }
                    }
                    return false;
                });
        assertTrue(loader.load());
        assertFalse(loader.componentEnabled(Lazy.class));
        var executor = Executors.newSingleThreadExecutor();
        try {
            var result = executor.submit(() -> loader.componentEnabled(Lazy.class));
            assertTrue(checking.await(5, TimeUnit.SECONDS));
            loader.unload();
            release.countDown();
            assertFalse(result.get(5, TimeUnit.SECONDS));
            assertTrue(loader.diagnostics()
                    .snapshot()
                    .components()
                    .get(0)
                    .enabled()
                    .isEmpty());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    static class Resource implements Loader {
        private final WeftDiagnostics diagnostics;

        Resource(WeftDiagnostics diagnostics) {
            this.diagnostics = diagnostics;
        }

        @Override
        public boolean load() {
            var snapshot = diagnostics.snapshot();
            assertEquals(WeftDiagnostics.GraphState.LOADING, snapshot.state());
            assertEquals(
                    WeftDiagnostics.ComponentState.LOADING,
                    snapshot.components().get(0).state());
            return true;
        }

        @Override
        public void unload() {
            assertEquals(
                    WeftDiagnostics.GraphState.UNLOADING, diagnostics.snapshot().state());
        }
    }

    @Test
    void snapshotsWorkInsideLifecycleHooksAndRetainSuccessfulTimings() {
        WeftLoader loader = new WeftLoader(new TestRegistry()
                .eager(Resource.class, args -> new Resource((WeftDiagnostics) args[0]), on(WeftDiagnostics.class)));
        assertTrue(loader.load());
        var snapshot = loader.diagnostics().snapshot();
        assertEquals(
                WeftDiagnostics.ComponentState.LOADED,
                snapshot.components().get(0).state());
        assertTrue(snapshot.components().get(0).loadTime().isPresent());
        loader.unload();
        assertTrue(
                loader.diagnostics().snapshot().components().get(0).loadTime().isEmpty());
        assertTrue(snapshot.components().get(0).loadTime().isPresent());
    }

    @Test
    void injectedFacadeObservesWithoutMaterializingOrEvaluatingFlags() {
        AtomicInteger constructions = new AtomicInteger();
        AtomicInteger evaluations = new AtomicInteger();
        WeftLoader loader = new WeftLoader(
                new TestRegistry()
                        .eager(
                                Consumer.class,
                                args -> new Consumer((WeftDiagnostics) args[0]),
                                on(WeftDiagnostics.class))
                        .lazy(Lazy.class, args -> {
                            constructions.incrementAndGet();
                            return new Lazy();
                        })
                        .flag(Lazy.class, new Flag("optional", false)),
                (FeatureFlags) key -> {
                    evaluations.incrementAndGet();
                    return false;
                });
        assertTrue(loader.load());
        assertSame(loader.diagnostics(), loader.get(Consumer.class).diagnostics());
        var snapshot = loader.diagnostics().snapshot();
        var lazy = snapshot.components().stream()
                .filter(c -> c.type() == Lazy.class)
                .findFirst()
                .orElseThrow();
        assertEquals(WeftDiagnostics.ComponentState.NOT_CREATED, lazy.state());
        assertTrue(lazy.enabled().isEmpty());
        assertEquals(0, constructions.get());
        assertEquals(0, evaluations.get());
        assertThrows(
                UnsupportedOperationException.class, () -> snapshot.components().clear());
        assertFalse(loader.diagnostics().snapshot().components().isEmpty());
        assertThrows(RuntimeException.class, () -> loader.create(Lazy.class));
        assertEquals(
                false,
                loader.diagnostics().snapshot().components().stream()
                        .filter(c -> c.type() == Lazy.class)
                        .findFirst()
                        .orElseThrow()
                        .enabled()
                        .orElseThrow());
        loader.unload();
        assertEquals(
                WeftDiagnostics.GraphState.UNLOADED,
                loader.diagnostics().snapshot().state());
        assertEquals(WeftDiagnostics.GraphState.LOADED, snapshot.state());
    }

    @Test
    void failedLazyComponentsExposeCopiedFailureUntilReset() {
        WeftLoader loader = new WeftLoader(new TestRegistry().lazy(Broken.class, args -> {
            throw new IllegalStateException("broken");
        }));
        assertTrue(loader.load());
        assertThrows(RuntimeException.class, () -> loader.create(Broken.class));
        var failed = loader.diagnostics().snapshot().components().get(0);
        assertEquals(WeftDiagnostics.ComponentState.FAILED, failed.state());
        assertTrue(failed.failure().isPresent());
        assertTrue(loader.resetFailure(Broken.class));
        assertEquals(
                WeftDiagnostics.ComponentState.NOT_CREATED,
                loader.diagnostics().snapshot().components().get(0).state());
        assertTrue(failed.failure().isPresent());
    }
}
