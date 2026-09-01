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
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.weftkit.wiring.runtime.WeftLoader;
import org.weftkit.wiring.runtime.exception.ComponentLoadException;
import org.weftkit.wiring.runtime.exception.ResolutionException;
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
        WeftLoader loader = new WeftLoader(WeftWiring.INSTANCE, wiring, probe);
        probe.loader = loader;
        return loader;
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
    void rejectsSecondAmbientValueForADeclaredTypeAtConstruction() {
        ResolutionException ex = assertThrows(
                ResolutionException.class,
                () -> new WeftLoader(WeftWiring.INSTANCE, wiring, probe, new Sample.Probe()));
        assertTrue(ex.getMessage().contains("Ambiguous ambient value"));
    }

    @Test
    void rejectsAmbientValueMatchingNoDeclaredType() {
        ResolutionException ex = assertThrows(
                ResolutionException.class, () -> new WeftLoader(WeftWiring.INSTANCE, wiring, probe, "stray"));
        assertTrue(ex.getMessage().contains("matches no declared ambient type"));
    }

    // A null is an absent value, not a stray one, so it is skipped rather than rejected
    @Test
    void skipsNullAmbientValue() {
        WeftLoader loader = assertDoesNotThrow(() -> new WeftLoader(WeftWiring.INSTANCE, wiring, probe, null));
        assertNull(loader.ambient(Sample.Cache.class));
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
        // Frozen is flagged off, so it is skipped without a timing entry
        assertEquals(
                loader.loadOrder().stream()
                        .filter(type -> !type.equals(Sample.Frozen.class))
                        .toList(),
                List.copyOf(loader.loadTimings().keySet()));
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
    void exposesUniqueAmbientByType() {
        WeftLoader loader = newLoader();
        assertSame(wiring, loader.ambient(Sample.Wiring.class));
        assertNull(loader.ambient(String.class));
    }

    @Test
    void declaredAmbientTypeWithoutValueFailsWithAHint() {
        WeftLoader loader = newLoader();
        loader.load();
        ResolutionException ex = assertThrows(ResolutionException.class, () -> loader.create(Sample.CacheUser.class));
        assertTrue(ex.getMessage().contains("value is absent"));
    }

    @Test
    void ambientMatchesThroughTheDeclaredTypeOnly() {
        // The value is a ChatNotifier at runtime, but only the declared Notifier slot holds it
        Sample.ChatNotifier ambientNotifier = new Sample.ChatNotifier();
        WeftLoader loader = new WeftLoader(WeftWiring.INSTANCE, wiring, probe, ambientNotifier);
        assertNull(loader.ambient(Sample.ChatNotifier.class));
        assertSame(ambientNotifier, loader.ambient(Sample.Notifier.class));
    }

    @Test
    void ambientValueShadowsTheWiredImplementation() {
        // Storage is declared ambient with SqlStorage wired, so the binding backs an absent value
        Sample.Storage fake = new Sample.Storage() {};
        WeftLoader loader = new WeftLoader(WeftWiring.INSTANCE, wiring, probe, fake);
        probe.loader = loader;
        loader.load();
        assertSame(fake, loader.create(Sample.Backup.class).storage());
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
    void resolvesOptionalDependencyOnDisabledFlaggedComponentToEmpty() {
        WeftLoader loader = newLoader();
        loader.load();
        // Frozen is gated off by default, so neither consumer receives it and it never loads
        assertEquals(Optional.empty(), loader.create(Sample.GatedUser.class).frozen());
        assertEquals(Optional.empty(), loader.get(Sample.GatedWatcher.class).frozen());
        assertFalse(probe.loads.contains("Frozen"));
    }

    @Test
    void resolvesOptionalDependencyOnEnabledFlaggedComponentToPresent() {
        probe.enabledFlags.add("frozen");
        WeftLoader loader = newLoader();
        loader.load();
        Sample.Frozen frozen = loader.get(Sample.Frozen.class);
        assertSame(frozen, loader.create(Sample.GatedUser.class).frozen().orElseThrow());
        // The eager consumer received the same singleton, so the flagged dependency loaded first
        assertSame(frozen, loader.get(Sample.GatedWatcher.class).frozen().orElseThrow());
        assertTrue(probe.loads.contains("Frozen"));
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
        probe.loader = loader;
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
    void firstAssignableArgumentWins() {
        WeftLoader loader = newLoader();
        loader.load();
        Sample.Widget first = new Sample.Widget();
        assertSame(
                first,
                loader.create(Sample.Consumer.class, first, new Sample.Widget()).widget());
    }

    @Test
    void createRejectsArgumentsForASingleton() {
        WeftLoader loader = newLoader();
        loader.load();
        ResolutionException ex =
                assertThrows(ResolutionException.class, () -> loader.create(Sample.Alpha.class, new Sample.Widget()));
        assertTrue(ex.getMessage().contains("never reach a singleton"));
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
    void lazyHookAbortThrowsAndStaysFailedUntilReset() {
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
        // The failure sticks: no rebuild, the cheap rethrow chains the first failure
        ComponentLoadException again =
                assertThrows(ComponentLoadException.class, () -> loader.create(Sample.FrostUser.class));
        assertTrue(again.getMessage().contains("stays failed until reset"));
        assertSame(ex, again.getCause());
        assertEquals(1, probe.loads.stream().filter("Frost"::equals).count());
        assertTrue(loader.resetFailure(Sample.Frost.class));
        assertSame(loader.create(Sample.FrostUser.class).frost(), loader.get(Sample.Frost.class));
        assertEquals(2, probe.loads.stream().filter("Frost"::equals).count());
        assertFalse(loader.resetFailure(Sample.Frost.class));
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
        loader.resetFailure(Sample.Frost.class);
        assertSame(loader.create(Sample.FrostUser.class).frost(), loader.get(Sample.Frost.class));
    }

    @Test
    void unloadClearsLazyFailureMarkers() {
        WeftLoader loader = newLoader();
        loader.load();
        probe.lazyThrowOnLoad = true;
        assertThrows(IllegalStateException.class, () -> loader.create(Sample.FrostUser.class));
        loader.unload();
        probe.lazyThrowOnLoad = false;
        loader.load();
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
        assertTrue(loader.resetFailure(Sample.GearProvider.class));
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
        Map<Class<?>, Duration> beforeFrost = loader.loadTimings();
        assertFalse(beforeFrost.containsKey(Sample.Frost.class));
        loader.create(Sample.FrostUser.class);
        assertTrue(loader.loadTimings().containsKey(Sample.Frost.class));
        // Timings are a snapshot, the earlier map does not grow behind the caller
        assertFalse(beforeFrost.containsKey(Sample.Frost.class));
    }
}
