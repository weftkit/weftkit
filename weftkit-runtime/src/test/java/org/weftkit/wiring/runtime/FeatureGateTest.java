package org.weftkit.wiring.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.weftkit.wiring.registry.Flag;

class FeatureGateTest {

    private static final Flag OFF_BY_DEFAULT = new Flag("off", false);

    private static final Flag ON_BY_DEFAULT = new Flag("on", true);

    @Test
    void unflaggedComponentIsAlwaysOn() {
        assertTrue(FeatureGate.enabled(null, null));
        assertTrue(FeatureGate.enabled(null, key -> false));
    }

    @Test
    void withoutFlagsTheDefaultDecides() {
        assertTrue(FeatureGate.enabled(ON_BY_DEFAULT, null));
        assertFalse(FeatureGate.enabled(OFF_BY_DEFAULT, null));
    }

    @Test
    void presentFlagsDecideByKey() {
        assertFalse(FeatureGate.enabled(ON_BY_DEFAULT, key -> false));
        assertTrue(FeatureGate.enabled(OFF_BY_DEFAULT, "off"::equals));
    }
}
