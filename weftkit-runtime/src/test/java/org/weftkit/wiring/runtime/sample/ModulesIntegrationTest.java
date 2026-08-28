package org.weftkit.wiring.runtime.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.weftkit.wiring.runtime.Modules;
import org.weftkit.wiring.runtime.WeftLoader;

class ModulesIntegrationTest {

    private Sample.Probe probe;
    private Sample.Wiring wiring;

    @BeforeEach
    void setUp() {
        probe = new Sample.Probe();
        wiring = new Sample.Wiring(probe);
    }

    private WeftLoader loadedLoader() {
        WeftLoader loader = new WeftLoader(WeftWiring.INSTANCE, wiring, probe);
        probe.loader = loader;
        assertTrue(loader.load());
        return loader;
    }

    @Test
    void registryListsModulesInLoadOrder() {
        assertEquals(List.of(Sample.ZuluModule.class, Sample.AlphaModule.class), WeftWiring.INSTANCE.modules());
    }

    @Test
    void activatesInLoadOrderAndDeactivatesInReverse() {
        WeftLoader loader = loadedLoader();
        assertTrue(Modules.activate(loader));
        assertEquals(List.of("Zulu", "Alpha"), probe.activations);
        Modules.deactivate(loader);
        assertEquals(List.of("Alpha", "Zulu"), probe.deactivations);
    }

    @Test
    void modulesAreOrdinaryGraphSingletons() {
        WeftLoader loader = loadedLoader();
        assertSame(loader.get(Sample.ZuluModule.class), loader.get(Sample.ZuluModule.class));
        assertTrue(loader.loadOrder().contains(Sample.AlphaModule.class));
    }

    @Test
    void moduleAbortDeactivatesTheActivatedPrefix() {
        probe.alphaModuleProceed = false;
        WeftLoader loader = loadedLoader();
        assertFalse(Modules.activate(loader));
        assertEquals(List.of("Zulu"), probe.deactivations);
    }

    @Test
    void moduleThrowRollsBackAndRethrows() {
        probe.alphaModuleThrow = true;
        WeftLoader loader = loadedLoader();
        assertThrows(IllegalStateException.class, () -> Modules.activate(loader));
        assertEquals(List.of("Zulu"), probe.deactivations);
    }
}
