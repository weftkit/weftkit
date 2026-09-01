package org.weftkit.wiring.runtime.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.weftkit.wiring.runtime.WeftLoader;
import org.weftkit.wiring.runtime.WeftView;

// Pins every rule that arises from the interplay of two features, so a resolution refactor
// cannot change one unnoticed
class CombinationRulesTest {

    private Sample.Probe probe;
    private Sample.Wiring wiring;

    @BeforeEach
    void setUp() {
        probe = new Sample.Probe();
        wiring = new Sample.Wiring(probe);
    }

    private WeftLoader newLoader(Object... extraAmbient) {
        Object[] ambient = Stream.concat(Stream.of(wiring, probe), Arrays.stream(extraAmbient))
                .toArray();
        WeftLoader loader = new WeftLoader(WeftWiring.INSTANCE, ambient);
        probe.loader = loader;
        return loader;
    }

    @Test
    void optionalProductOfAGatedOffLazyOwnerStaysEmpty() {
        WeftLoader loader = newLoader();
        loader.load();
        assertEquals(
                Optional.empty(), loader.create(Sample.OptionalToolUser.class).tool());
        // The flag blocks the materialization an optional product would otherwise trigger
        assertFalse(probe.loads.contains("ToolProvider"));
    }

    @Test
    void optionalProductOfAnEnabledFlaggedLazyOwnerMaterializesIt() {
        probe.enabledFlags.add("tools");
        WeftLoader loader = newLoader();
        loader.load();
        Optional<Sample.Tool> tool =
                loader.create(Sample.OptionalToolUser.class).tool();
        assertSame(loader.get(Sample.ToolProvider.class).tool(), tool.orElseThrow());
        assertEquals(1, probe.loads.stream().filter("ToolProvider"::equals).count());
    }

    @Test
    void qualifiedDependencyIgnoresAMatchingArgument() {
        WeftLoader loader = newLoader();
        loader.load();
        // Mirrors qualifiedDependencyIgnoresAmbientOfSameType for the argument half of the rule
        Sample.Widget widget =
                loader.create(Sample.SpareUser.class, new Sample.Widget()).widget();
        assertSame(loader.get(Sample.Provider.class).spare(), widget);
    }

    @Test
    void argumentWinsOverAnAmbientValueOfTheSameType() {
        WeftLoader loader = newLoader();
        loader.load();
        Sample.Probe other = new Sample.Probe();
        assertSame(other, loader.create(Sample.Reporter.class, other).probe());
        assertSame(probe, loader.create(Sample.Reporter.class).probe());
    }

    @Test
    void argumentSatisfiesAnOptionalDependency() {
        WeftLoader loader = newLoader();
        loader.load();
        Sample.Cache cache = new Sample.Cache() {};
        assertEquals(
                Optional.of(cache), loader.create(Sample.Tuner.class, cache).cache());
    }

    @Test
    void ambientValueSatisfiesAnOptionalDependency() {
        Sample.Cache cache = new Sample.Cache() {};
        WeftLoader loader = newLoader(cache);
        loader.load();
        assertEquals(Optional.of(cache), loader.create(Sample.Tuner.class).cache());
    }

    @Test
    void ambientValueSatisfiesARequiredDependency() {
        Sample.Cache cache = new Sample.Cache() {};
        WeftLoader loader = newLoader(cache);
        loader.load();
        assertSame(cache, loader.create(Sample.CacheUser.class).cache());
    }

    @Test
    void optionalProductDependencyResolvesEmptyBeforeItsOwnerLoads() {
        WeftLoader loader = newLoader();
        // No load() ran, so the eager Provider has not captured its Widget yet
        assertEquals(Optional.empty(), loader.create(Sample.Tuner.class).widget());
    }

    @Test
    void flagChecksStayLiveWhileTheProviderInstanceIsCached() {
        WeftLoader loader = newLoader();
        loader.load();
        WeftView view = loader.create(Sample.WeftUser.class).view();
        assertFalse(view.enabled(Sample.ToolProvider.class));
        assertEquals(
                Optional.empty(), loader.create(Sample.OptionalToolUser.class).tool());
        probe.enabledFlags.add("tools");
        // The cached ProbeFlags instance answers live, so the lazy owner materializes now
        assertTrue(view.enabled(Sample.ToolProvider.class));
        assertTrue(loader.create(Sample.OptionalToolUser.class).tool().isPresent());
    }

    @Test
    void resetFailureResolvesInterfaceBindings() {
        WeftLoader loader = newLoader();
        loader.load();
        probe.lazyThrowOnLoad = true;
        assertThrows(IllegalStateException.class, () -> loader.create(Sample.ChillerUser.class));
        probe.lazyThrowOnLoad = false;
        // The marker sits on Frost, the bound implementation, yet the interface clears it
        assertTrue(loader.resetFailure(Sample.Chiller.class));
        assertSame(loader.create(Sample.ChillerUser.class).chiller(), loader.get(Sample.Chiller.class));
    }
}
