package org.weftkit.wiring.runtime.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.weftkit.wiring.runtime.FeatureFlags;
import org.weftkit.wiring.runtime.Modules;
import org.weftkit.wiring.runtime.ResolutionException;
import org.weftkit.wiring.runtime.Weft;
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

    @Test
    void flaggedComponentIsNeverConstructedWhileItsFlagIsOff() {
        WeftLoader loader = loadedLoader();
        assertFalse(probe.loads.contains("Frozen"));
        assertTrue(loader.createAll(Sample.GatedService.class).isEmpty());
        assertThrows(ResolutionException.class, () -> loader.create(Sample.Frozen.class));
    }

    @Test
    void wiredFeatureFlagsImplementationDecides() {
        probe.enabledFlags.add("frozen");
        WeftLoader loader = loadedLoader();
        assertTrue(probe.loads.contains("Frozen"));
        assertEquals(1, loader.createAll(Sample.GatedService.class).size());
    }

    @Test
    void ambientFeatureFlagsWinOverTheWiredImplementation() {
        FeatureFlags allOn = key -> true;
        WeftLoader loader = new WeftLoader(WeftWiring.INSTANCE, wiring, probe, allOn);
        probe.loader = loader;
        assertTrue(loader.load());
        // The wired ProbeFlags would report the flag off, the ambient value enables it
        assertTrue(probe.loads.contains("Frozen"));
    }

    @Test
    void weftFacadeEnumeratesEnabledComponentsOnly() {
        WeftLoader loader = loadedLoader();
        Weft weft = loader.create(Sample.WeftUser.class).weft();
        assertEquals(2, weft.all(Sample.Service.class).size());
        assertThrows(ResolutionException.class, () -> weft.all(Object.class));
    }
}
