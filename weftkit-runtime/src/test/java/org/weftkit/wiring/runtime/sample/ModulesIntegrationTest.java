package org.weftkit.wiring.runtime.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.weftkit.wiring.FeatureFlags;
import org.weftkit.wiring.runtime.Annotated;
import org.weftkit.wiring.runtime.Modules;
import org.weftkit.wiring.runtime.Weft;
import org.weftkit.wiring.runtime.WeftLoader;
import org.weftkit.wiring.runtime.WeftView;
import org.weftkit.wiring.runtime.exception.ResolutionException;

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

    @Test
    void weftFacadeEnumeratesAnnotatedComponentsOnly() {
        WeftLoader loader = loadedLoader();
        Weft weft = loader.create(Sample.WeftUser.class).weft();
        List<Annotated<Sample.Service, Sample.Marked>> marked =
                weft.annotated(Sample.Service.class, Sample.Marked.class);
        assertEquals(1, marked.size());
        assertTrue(marked.get(0).component() instanceof Sample.Worker);
        assertEquals("marked", marked.get(0).annotation().value());
    }

    @Test
    void pairsAComponentOncePerRepeatedAnnotation() {
        Weft weft = loadedLoader().create(Sample.WeftUser.class).weft();
        List<Annotated<Sample.Service, Sample.Tagged>> tagged =
                weft.annotated(Sample.Service.class, Sample.Tagged.class);
        assertEquals(
                List.of("first", "second"),
                tagged.stream().map(entry -> entry.annotation().value()).toList());
        assertSame(tagged.get(0).component(), tagged.get(1).component());
    }

    @Test
    void recordedAnnotationsHonorTheAnnotationContract() {
        Weft weft = loadedLoader().create(Sample.WeftUser.class).weft();
        Sample.Marked recorded =
                weft.annotated(Sample.Service.class, Sample.Marked.class).get(0).annotation();
        Sample.Marked reflected = Sample.Worker.class.getAnnotation(Sample.Marked.class);
        assertEquals(reflected, recorded);
        assertEquals(recorded, reflected);
        assertEquals(reflected.hashCode(), recorded.hashCode());
        assertTrue(recorded.toString().contains("value=marked"));
    }

    @Test
    void namesAnnotationsWhoseHandlingModuleIsMissing() {
        assertEquals(List.of(Sample.Orphaned.class), Modules.unhandled(loadedLoader()));
    }

    @Test
    void namesUnhandledAnnotationsOfFlaggedComponentsOnlyWhileOn() {
        probe.enabledFlags.add("frozen");
        assertEquals(List.of(Sample.Orphaned.class, Sample.Silenced.class), Modules.unhandled(loadedLoader()));
    }

    @Test
    void weftViewNamesEnabledTypesWithoutConstructingThem() {
        WeftLoader loader = loadedLoader();
        WeftView view = loader.create(Sample.WeftUser.class).view();
        assertEquals(List.of(Sample.Consumer.class, Sample.Worker.class), view.types(Sample.Service.class));
        // A flagged component is named by neither weft.all nor view.types while its flag is off
        assertTrue(view.types(Sample.GatedService.class).isEmpty());
        assertThrows(ResolutionException.class, () -> view.types(Object.class));
        List<Class<? extends Sample.Service>> types = view.types(Sample.Service.class);
        assertThrows(UnsupportedOperationException.class, () -> types.remove(Sample.Consumer.class));
    }

    @Test
    void weftViewAnswersMembershipAndFlagState() {
        WeftLoader loader = loadedLoader();
        WeftView view = loader.create(Sample.WeftUser.class).view();
        assertTrue(view.contains(Sample.Frozen.class));
        // Present but gated off, so not enabled and never constructed
        assertFalse(view.enabled(Sample.Frozen.class));
        assertFalse(probe.loads.contains("Frozen"));
        // A supertype with no single binding is not itself a component
        assertFalse(view.contains(Sample.Service.class));
    }

    @Test
    void weftViewReportsLoadOrderTimingsAndModules() {
        WeftLoader loader = loadedLoader();
        WeftView view = loader.create(Sample.WeftUser.class).view();
        assertEquals(loader.loadOrder(), view.loadOrder());
        assertEquals(List.of(Sample.ZuluModule.class, Sample.AlphaModule.class), view.modules());
        assertTrue(view.loadTimings().containsKey(Sample.ZuluModule.class));
        assertEquals(loader.totalLoadTime(), view.totalLoadTime());
    }
}
