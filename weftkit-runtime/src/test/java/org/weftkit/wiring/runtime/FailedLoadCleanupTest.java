package org.weftkit.wiring.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.weftkit.wiring.Loader;
import org.weftkit.wiring.registry.TestRegistry;
import org.weftkit.wiring.runtime.exception.ComponentLoadException;

class FailedLoadCleanupTest {

    @Test
    void eagerDeclineReleasesPartiallyAcquiredResourcesExactlyOnce() {
        Resource resource = new Resource();
        WeftLoader loader = new WeftLoader(new TestRegistry().eager(Resource.class, args -> resource));
        assertFalse(loader.load());
        assertClean(loader, resource);
    }

    @Test
    void lazyDeclineReleasesResourcesAndKeepsItsFailureSticky() {
        Resource resource = new Resource();
        WeftLoader loader = new WeftLoader(new TestRegistry().lazy(Resource.class, args -> resource));
        assertTrue(loader.load());
        assertThrows(ComponentLoadException.class, () -> loader.create(Resource.class));
        assertThrows(ComponentLoadException.class, () -> loader.create(Resource.class));
        assertClean(loader, resource);
    }

    @Test
    void failedLoadPreservesItsExceptionAndSuppressesCleanupFailure() {
        Resource resource = new Resource();
        resource.loadFailure = new IllegalStateException("load failed");
        resource.unloadFailure = new IllegalStateException("cleanup failed");
        WeftLoader loader = new WeftLoader(new TestRegistry().eager(Resource.class, args -> resource));
        RuntimeException failure = assertThrows(IllegalStateException.class, loader::load);
        assertSame(resource.loadFailure, failure);
        assertEquals(1, failure.getSuppressed().length);
        assertSame(resource.unloadFailure, failure.getSuppressed()[0]);
        assertClean(loader, resource);
    }

    @Test
    void declinedLoadReportsCleanupFailure() {
        Resource resource = new Resource();
        resource.unloadFailure = new IllegalStateException("cleanup failed");
        WeftLoader loader = new WeftLoader(new TestRegistry().eager(Resource.class, args -> resource));
        assertSame(resource.unloadFailure, assertThrows(IllegalStateException.class, loader::load));
        assertClean(loader, resource);
    }

    private void assertClean(WeftLoader loader, Resource resource) {
        assertFalse(resource.acquired);
        assertNull(loader.get(Resource.class));
        loader.unload();
        assertEquals(1, resource.unloads);
    }

    private static final class Resource implements Loader {
        boolean acquired;
        int unloads;
        RuntimeException loadFailure;
        RuntimeException unloadFailure;

        @Override
        public boolean load() {
            acquired = true;
            if (loadFailure != null) throw loadFailure;
            return false;
        }

        @Override
        public void unload() {
            acquired = false;
            unloads++;
            if (unloadFailure != null) throw unloadFailure;
        }
    }
}
