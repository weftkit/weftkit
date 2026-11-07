package org.weftkit.wiring.runtime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.weftkit.wiring.Loader;
import org.weftkit.wiring.registry.ComponentRegistry;
import org.weftkit.wiring.registry.TestRegistry;

class EagerProductCaptureTest {

    public static final class Blank {}

    public static final class Forgetful implements Loader {

        @Override
        public boolean load() {
            return true;
        }

        public Blank blank() {
            return null;
        }
    }

    private static final ComponentRegistry REGISTRY = new TestRegistry()
            .eager(Forgetful.class, arguments -> new Forgetful())
            .product(Blank.class, Forgetful.class, owner -> ((Forgetful) owner).blank());

    @Test
    void failsLoadWhenAProductIsNullAfterItsOwnerLoaded() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, new WeftLoader(REGISTRY)::load);
        assertTrue(ex.getMessage().contains("null after load"));
    }
}
