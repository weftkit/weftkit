package org.weftkit.wiring.runtime.sample;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.weftkit.wiring.runtime.ComponentLoadException;
import org.weftkit.wiring.runtime.ResolutionException;
import org.weftkit.wiring.runtime.WeftLoader;
import org.weftkit.wiring.runtime.sample.internal.Machine;

class WeftLoaderIntegrationTest {

    private Sample.Probe probe;
    private Sample.Wiring wiring;

    @BeforeEach
    void setUp() {
        probe = new Sample.Probe();
        wiring = new Sample.Wiring(probe);
    }

    private WeftLoader newLoader() {
        return new WeftLoader(WeftWiring.INSTANCE, wiring, probe);
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
        WeftLoader loader = new WeftLoader(WeftWiring.INSTANCE, wiring, probe, new Sample.Probe());
        ResolutionException ex = assertThrows(ResolutionException.class, loader::load);
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
        assertEquals(WeftWiring.INSTANCE.loadOrder(), loader.loadOrder());
        assertEquals(loader.loadOrder(), List.copyOf(loader.loadTimings().keySet()));
        assertEquals(
                loader.loadTimings().values().stream().reduce(Duration.ZERO, Duration::plus), loader.totalLoadTime());
        assertFalse(loader.totalLoadTime().isNegative());
    }

    @Test
    void wiresPackagePrivateComponentsThroughTheirPackageFragment() {
        WeftLoader loader = newLoader();
        assertTrue(loader.load());
        Machine machine = loader.get(Machine.class);
        assertEquals("engine+machine", machine.signature());
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
        WeftLoader loader = new WeftLoader(WeftWiring.INSTANCE, wiring, probe, ambientNotifier);
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

    @Test
    void runsLazyLoaderHookOnFirstMaterializationOnly() {
        WeftLoader loader = newLoader();
        loader.load();
        assertFalse(probe.loads.contains("Frost"));
        Sample.Frost frost = loader.create(Sample.FrostUser.class).frost();
        assertSame(frost, loader.create(Sample.FrostUser.class).frost());
        assertSame(frost, loader.get(Sample.Frost.class));
        assertEquals(1, probe.loads.stream().filter("Frost"::equals).count());
    }

    @Test
    void lazyHookAbortThrowsAndLeavesGraphUpForRetry() {
        WeftLoader loader = newLoader();
        loader.load();
        probe.lazyProceed = false;
        ComponentLoadException ex =
                assertThrows(ComponentLoadException.class, () -> loader.create(Sample.FrostUser.class));
        assertTrue(ex.getMessage().contains("aborted"));
        assertNull(loader.get(Sample.Frost.class));
        assertSame(loader.get(Sample.Alpha.class), loader.get(Sample.Alpha.class));
        assertTrue(probe.unloads.isEmpty());
        probe.lazyProceed = true;
        assertSame(loader.create(Sample.FrostUser.class).frost(), loader.get(Sample.Frost.class));
        assertEquals(2, probe.loads.stream().filter("Frost"::equals).count());
    }

    @Test
    void lazyHookThrowDropsTheSingletonWithoutItsUnloadHook() {
        WeftLoader loader = newLoader();
        loader.load();
        probe.lazyThrowOnLoad = true;
        assertThrows(IllegalStateException.class, () -> loader.create(Sample.FrostUser.class));
        assertFalse(probe.unloads.contains("Frost"));
        assertNull(loader.get(Sample.Frost.class));
        probe.lazyThrowOnLoad = false;
        assertSame(loader.create(Sample.FrostUser.class).frost(), loader.get(Sample.Frost.class));
    }

    @Test
    void unloadsLazySingletonsInReverseCreationOrder() {
        WeftLoader loader = newLoader();
        loader.load();
        loader.create(Sample.FrostUser.class);
        loader.unload();
        assertTrue(probe.unloads.contains("Frost"));
        // Frost materialized after load, so it is torn down before every eager loader
        assertTrue(probe.unloads.indexOf("Frost") < probe.unloads.indexOf("Gate"));
        assertTrue(probe.unloads.indexOf("Gate") < probe.unloads.indexOf("Beta"));
        assertTrue(probe.unloads.indexOf("Beta") < probe.unloads.indexOf("Alpha"));
    }

    @Test
    void materializesLazyOwnerForItsProduct() {
        WeftLoader loader = newLoader();
        loader.load();
        assertFalse(probe.loads.contains("GearProvider"));
        Sample.Gear gear = loader.create(Sample.GearUser.class).gear();
        assertSame(loader.get(Sample.GearProvider.class).gear(), gear);
        assertSame(gear, loader.create(Sample.GearUser.class).gear());
        assertEquals(1, probe.loads.stream().filter("GearProvider"::equals).count());
    }

    @Test
    void reMaterializesLazyOwnerAfterUnload() {
        WeftLoader loader = newLoader();
        loader.load();
        Sample.Gear first = loader.create(Sample.GearUser.class).gear();
        loader.unload();
        assertNotSame(first, loader.create(Sample.GearUser.class).gear());
    }

    @Test
    void nullLazyProductFailsMaterializationAndLeavesNoStaleState() {
        WeftLoader loader = newLoader();
        loader.load();
        probe.lazyNullProduct = true;
        ResolutionException ex = assertThrows(ResolutionException.class, () -> loader.create(Sample.GearUser.class));
        assertTrue(ex.getMessage().contains("null after load"));
        // The hook completed before the capture failed, so the owner was torn down again
        assertTrue(probe.unloads.contains("GearProvider"));
        assertNull(loader.get(Sample.GearProvider.class));
        probe.lazyNullProduct = false;
        Sample.Gear spare = loader.create(Sample.SpareGearUser.class).gear();
        assertSame(loader.get(Sample.GearProvider.class).spareGear(), spare);
    }

    @Test
    void optionalLazyProductMaterializesItsOwner() {
        WeftLoader loader = newLoader();
        loader.load();
        Optional<Sample.Gear> gear =
                loader.create(Sample.OptionalGearUser.class).gear();
        assertTrue(gear.isPresent());
        assertTrue(probe.loads.contains("GearProvider"));
    }

    @Test
    void lazyHookMaterializingAnotherLazyUnloadsTheConsumerFirst() {
        WeftLoader loader = newLoader();
        loader.load();
        loader.create(Sample.Chain.class);
        assertEquals(1, probe.loads.stream().filter("Frost"::equals).count());
        loader.unload();
        // Frost completed inside Chain's hook, so Chain tears down first
        assertTrue(probe.unloads.indexOf("Chain") < probe.unloads.indexOf("Frost"));
    }

    @Test
    void lazyInitializerMaterializesBeforeItsRequiringComponent() {
        WeftLoader loader = newLoader();
        loader.load();
        assertFalse(probe.loads.contains("LazyIniter"));
        assertEquals(7, loader.create(Sample.LazyReader.class).seen());
        loader.create(Sample.LazyReader.class);
        assertEquals(1, probe.loads.stream().filter("LazyIniter"::equals).count());
    }

    @Test
    void recordsLazyMaterializationTimings() {
        WeftLoader loader = newLoader();
        loader.load();
        assertFalse(loader.loadTimings().containsKey(Sample.Frost.class));
        loader.create(Sample.FrostUser.class);
        assertTrue(loader.loadTimings().containsKey(Sample.Frost.class));
    }
}
