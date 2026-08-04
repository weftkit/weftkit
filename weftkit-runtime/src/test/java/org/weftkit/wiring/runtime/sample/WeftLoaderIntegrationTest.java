package org.weftkit.wiring.runtime.sample;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.weftkit.wiring.runtime.ComponentLoadException;
import org.weftkit.wiring.runtime.WeftLoader;

class WeftLoaderIntegrationTest {

    private Sample.Probe probe;
    private Sample.Wiring wiring;

    @BeforeEach
    void setUp() {
        probe = new Sample.Probe();
        wiring = new Sample.Wiring(probe);
    }

    private WeftLoader newLoader() {
        return new WeftLoader(WiredComponents.INSTANCE, wiring, probe);
    }

    @Test
    void loadsSingletonsInDependencyOrder() {
        assertTrue(newLoader().load());
        assertTrue(probe.loads.indexOf("Alpha") < probe.loads.indexOf("Beta"));
        assertTrue(probe.loads.indexOf("Beta") < probe.loads.indexOf("Gate"));
        assertTrue(probe.loads.indexOf("Initer") < probe.loads.indexOf("Reader"));
    }

    @Test
    void injectsSingletonAmbientAndRegistryType() {
        WeftLoader loader = newLoader();
        loader.load();
        Sample.Beta beta = loader.get(Sample.Beta.class);
        assertSame(loader.get(Sample.Alpha.class), beta.alpha());
        assertSame(wiring, beta.wiring());
    }

    @Test
    void resolvesProvidedProductIntoFreshComponent() {
        WeftLoader loader = newLoader();
        loader.load();
        Sample.Consumer consumer = loader.create(Sample.Consumer.class);
        assertSame(loader.get(Sample.Provider.class).widget(), consumer.widget());
        assertNotSame(consumer, loader.create(Sample.Consumer.class));
    }

    @Test
    void createAllCollectsMatchingComponents() {
        WeftLoader loader = newLoader();
        loader.load();
        List<Sample.Service> services = loader.createAll(Sample.Service.class);
        assertEquals(2, services.size());
    }

    @Test
    void requiresOrdersAfterInitializes() {
        newLoader().load();
        assertEquals(42, probe.flagValue);
    }

    @Test
    void unloadsInReverseOrderAndIsIdempotent() {
        WeftLoader loader = newLoader();
        loader.load();
        loader.unload();
        assertTrue(probe.unloads.indexOf("Gate") < probe.unloads.indexOf("Beta"));
        assertTrue(probe.unloads.indexOf("Beta") < probe.unloads.indexOf("Alpha"));
        assertNull(loader.get(Sample.Alpha.class));
        assertDoesNotThrow(loader::unload);
    }

    @Test
    void abortTearsDownLoadedSingletons() {
        probe.proceed = false;
        WeftLoader loader = newLoader();
        assertFalse(loader.load());
        assertTrue(probe.unloads.indexOf("Beta") < probe.unloads.indexOf("Alpha"));
        assertFalse(probe.unloads.contains("Gate"));
    }

    @Test
    void unloadAggregatesTeardownFailures() {
        WeftLoader loader = newLoader();
        loader.load();
        probe.faultOnUnload = true;
        assertThrows(IllegalStateException.class, loader::unload);
        assertTrue(probe.unloads.contains("Alpha"));
    }

    @Test
    void wrapsConstructorFailureInComponentLoadException() {
        probe.explode = true;
        WeftLoader loader = newLoader();
        assertThrows(ComponentLoadException.class, loader::load);
        // Singletons loaded before the failing constructor are torn back down in reverse order
        assertTrue(probe.unloads.indexOf("Gate") < probe.unloads.indexOf("Beta"));
        assertTrue(probe.unloads.indexOf("Beta") < probe.unloads.indexOf("Alpha"));
        assertNull(loader.get(Sample.Alpha.class));
    }

    @Test
    void loadHookThrowTearsDownWithoutUnloadingTheThrower() {
        probe.throwOnLoad = true;
        WeftLoader loader = newLoader();
        assertThrows(IllegalStateException.class, loader::load);
        assertFalse(probe.unloads.contains("Gate"));
        assertTrue(probe.unloads.indexOf("Beta") < probe.unloads.indexOf("Alpha"));
        assertNull(loader.get(Sample.Beta.class));
    }

    @Test
    void rejectsAmbiguousAmbientValue() {
        WeftLoader loader = new WeftLoader(WiredComponents.INSTANCE, wiring, probe, new Sample.Probe());
        IllegalStateException ex = assertThrows(IllegalStateException.class, loader::load);
        assertTrue(ex.getMessage().contains("Ambiguous ambient value"));
    }

    @Test
    void lazySingletonIgnoresCallerArguments() {
        WeftLoader loader = newLoader();
        loader.load();
        Sample.Probe other = new Sample.Probe();
        List<Sample.Cold> colds = loader.createAll(Sample.Cold.class, other);
        assertEquals(1, colds.size());
        // The cached instance is built from the graph, not from whichever call materialized it
        assertTrue(probe.loads.contains("Cold"));
        assertFalse(other.loads.contains("Cold"));
        assertSame(colds.get(0), loader.get(Sample.Cold.class));
    }

    @Test
    void exposesLoadOrderAndTimings() {
        WeftLoader loader = newLoader();
        loader.load();
        assertEquals(WiredComponents.INSTANCE.loadOrder(), loader.loadOrder());
        assertEquals(loader.loadOrder(), List.copyOf(loader.loadTimings().keySet()));
    }

    @Test
    void injectsTheLoaderItself() {
        WeftLoader loader = newLoader();
        loader.load();
        assertSame(loader, loader.create(Sample.LoaderUser.class).loader());
    }

    @Test
    void bindsInterfaceToItsSingleImplementation() {
        WeftLoader loader = newLoader();
        loader.load();
        Sample.SqlStorage implementation = loader.get(Sample.SqlStorage.class);
        assertSame(implementation, loader.get(Sample.Storage.class));
        assertSame(implementation, loader.create(Sample.Backup.class).storage());
    }

    @Test
    void injectsPresentAndEmptyOptionals() {
        WeftLoader loader = newLoader();
        loader.load();
        Sample.Tuner tuner = loader.create(Sample.Tuner.class);
        assertEquals(Optional.of(loader.get(Sample.Provider.class).widget()), tuner.widget());
        assertEquals(Optional.empty(), tuner.cache());
    }

    @Test
    void materializesLazySingletonOnFirstInjection() {
        WeftLoader loader = newLoader();
        loader.load();
        assertFalse(probe.loads.contains("Cold"));
        assertNull(loader.get(Sample.Cold.class));
        Sample.Cold cold = loader.create(Sample.ColdUser.class).cold();
        assertSame(cold, loader.create(Sample.ColdUser.class).cold());
        assertSame(cold, loader.get(Sample.Cold.class));
        assertEquals(1, probe.loads.stream().filter("Cold"::equals).count());
        loader.unload();
        assertNull(loader.get(Sample.Cold.class));
    }

    @Test
    void injectsQualifiedImplementationAndProduct() {
        WeftLoader loader = newLoader();
        loader.load();
        assertSame(
                loader.get(Sample.ChatNotifier.class),
                loader.create(Sample.Alerts.class).notifier());
        Sample.Provider provider = loader.get(Sample.Provider.class);
        assertSame(provider.spare(), loader.create(Sample.SpareUser.class).widget());
        assertNotSame(provider.widget(), loader.create(Sample.SpareUser.class).widget());
    }

    @Test
    void qualifiedDependencyIgnoresAmbientOfSameType() {
        Sample.Notifier ambientNotifier = new Sample.Notifier() {};
        WeftLoader loader = new WeftLoader(WiredComponents.INSTANCE, wiring, probe, ambientNotifier);
        loader.load();
        assertSame(
                loader.get(Sample.ChatNotifier.class),
                loader.create(Sample.Alerts.class).notifier());
    }

    @Test
    void dropsCapturedProductsOnUnload() {
        WeftLoader loader = newLoader();
        loader.load();
        loader.unload();
        assertThrows(IllegalStateException.class, () -> loader.create(Sample.Consumer.class));
    }

    @Test
    void resolvesOwnProductDuringLoadHook() {
        WeftLoader loader = newLoader();
        assertTrue(loader.load());
        Sample.SelfProvider provider = loader.get(Sample.SelfProvider.class);
        assertSame(provider.gadget(), provider.observed);
    }

    @Test
    void rejectsAmbiguousArgument() {
        WeftLoader loader = newLoader();
        loader.load();
        assertThrows(
                IllegalStateException.class,
                () -> loader.create(Sample.Consumer.class, new Sample.Widget(), new Sample.Widget()));
    }
}
