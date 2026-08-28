package org.weftkit.wiring.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.weftkit.wiring.FeatureFlag;

class FeatureGateTest {

    @FeatureFlag(value = "off", enabledByDefault = false)
    private static final class OffByDefault {}

    @FeatureFlag("on")
    private static final class OnByDefault {}

    private static final class Unflagged {}

    @Test
    void unflaggedTypeIsAlwaysOn() {
        assertTrue(FeatureGate.enabled(Unflagged.class, null));
        assertTrue(FeatureGate.enabled(Unflagged.class, key -> false));
    }

    @Test
    void withoutFlagsTheDefaultDecides() {
        assertTrue(FeatureGate.enabled(OnByDefault.class, null));
        assertFalse(FeatureGate.enabled(OffByDefault.class, null));
    }

    @Test
    void presentFlagsDecideByKey() {
        assertFalse(FeatureGate.enabled(OnByDefault.class, key -> false));
        assertTrue(FeatureGate.enabled(OffByDefault.class, "off"::equals));
    }
}
