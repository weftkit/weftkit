package org.weftkit.wiring.runtime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.weftkit.wiring.registry.TestRegistry.on;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.weftkit.wiring.FeatureFlags;
import org.weftkit.wiring.registry.ComponentRegistry;
import org.weftkit.wiring.registry.Flag;
import org.weftkit.wiring.registry.TestRegistry;
import org.weftkit.wiring.runtime.exception.ComponentLoadException;
import org.weftkit.wiring.runtime.exception.ResolutionException;

// Hand-built registries reach the runtime flag guards that the processor rejects at compile
// time, and the FeatureFlags resolution corners the generated sample cannot express
class FlagResolutionTest {

    private static final class Events {

        final List<String> log = new ArrayList<>();

        boolean flagsConstructible = true;

        boolean flagAnswer = true;
    }

    public static final class Gated {}

    public static final class RequiredUser {

        RequiredUser(Gated gated) {}
    }

    public static final class Token {}

    public static final class GatedProvider {

        private final Token token = new Token();

        GatedProvider(Events events) {
            events.log.add("provider");
        }

        Token token() {
            return token;
        }
    }

    public static final class TokenUser {

        private final Token token;

        TokenUser(Token token) {
            this.token = token;
        }

        Token token() {
            return token;
        }
    }

    public static final class Switchboard implements FeatureFlags {

        private final Events events;

        Switchboard(Events events) {
            if (!events.flagsConstructible) throw new IllegalStateException("flags unavailable");
            events.log.add("switchboard");
            this.events = events;
        }

        @Override
        public boolean isEnabled(String key) {
            return events.flagAnswer;
        }
    }

    public static final class RivalSwitchboard implements FeatureFlags {

        RivalSwitchboard() {}

        @Override
        public boolean isEnabled(String key) {
            return true;
        }
    }

    // A hand-built registry declares no ambient types, so it keeps the permissive matching: a
    // value is injected by its runtime type, and neither an unmatched value nor a null is rejected
    @Test
    void handBuiltRegistryInjectsAmbientValuesByRuntimeType() {
        ComponentRegistry registry = new TestRegistry()
                .plain(TokenUser.class, arguments -> new TokenUser((Token) arguments[0]), on(Token.class));
        Token token = new Token();
        WeftLoader loader = new WeftLoader(registry, token, null);
        assertSame(token, loader.create(TokenUser.class).token());
    }

    @Test
    void requiredDependencyOnAGatedOffComponentFailsResolution() {
        ComponentRegistry registry = new TestRegistry()
                .plain(Gated.class, arguments -> new Gated())
                .plain(RequiredUser.class, arguments -> new RequiredUser((Gated) arguments[0]), on(Gated.class))
                .flag(Gated.class, new Flag("gated", false));
        WeftLoader loader = new WeftLoader(registry);
        ResolutionException ex = assertThrows(ResolutionException.class, () -> loader.create(RequiredUser.class));
        assertTrue(ex.getMessage().contains("gated off by its feature flag"));
    }

    @Test
    void requiredProductOfAGatedOffLazyOwnerIsNotAvailable() {
        Events events = new Events();
        ComponentRegistry registry = new TestRegistry()
                .lazy(GatedProvider.class, arguments -> new GatedProvider(events))
                .plain(TokenUser.class, arguments -> new TokenUser((Token) arguments[0]), on(Token.class))
                .product(Token.class, GatedProvider.class, owner -> ((GatedProvider) owner).token())
                .flag(GatedProvider.class, new Flag("provider", false));
        WeftLoader loader = new WeftLoader(registry);
        ResolutionException ex = assertThrows(ResolutionException.class, () -> loader.create(TokenUser.class));
        assertTrue(ex.getMessage().contains("not available yet"));
        // The gated-off owner was never constructed for its product
        assertTrue(events.log.isEmpty());
    }

    private static ComponentRegistry switchboardRegistry(Events events, boolean enabledByDefault) {
        return new TestRegistry()
                .plain(Gated.class, arguments -> new Gated())
                .lazy(Switchboard.class, arguments -> new Switchboard(events))
                .flag(Gated.class, new Flag("gated", enabledByDefault));
    }

    @Test
    void lazyFeatureFlagsImplementationMaterializesOnTheFirstFlagCheck() {
        Events events = new Events();
        WeftLoader loader = new WeftLoader(switchboardRegistry(events, false));
        assertDoesNotThrow(() -> loader.create(Gated.class));
        assertEquals(List.of("switchboard"), events.log);
        // The provider instance is cached, further checks do not rebuild it
        loader.create(Gated.class);
        assertEquals(List.of("switchboard"), events.log);
    }

    @Test
    void failedFeatureFlagsResolutionRethrowsInsteadOfDefaulting() {
        Events events = new Events();
        events.flagsConstructible = false;
        // The flag defaults on, so only a rethrow proves the failure was not cached as absent
        WeftLoader loader = new WeftLoader(switchboardRegistry(events, true));
        assertThrows(ComponentLoadException.class, () -> loader.create(Gated.class));
        assertThrows(ComponentLoadException.class, () -> loader.create(Gated.class));
        events.flagsConstructible = true;
        assertTrue(loader.resetFailure(Switchboard.class));
        assertDoesNotThrow(() -> loader.create(Gated.class));
    }

    @Test
    void ambiguousFeatureFlagsImplementationsFailResolution() {
        Events events = new Events();
        ComponentRegistry registry = new TestRegistry()
                .plain(Gated.class, arguments -> new Gated())
                .plain(Switchboard.class, arguments -> new Switchboard(events))
                .plain(RivalSwitchboard.class, arguments -> new RivalSwitchboard())
                .flag(Gated.class, new Flag("gated", false));
        WeftLoader loader = new WeftLoader(registry);
        ResolutionException ex = assertThrows(ResolutionException.class, () -> loader.create(Gated.class));
        assertTrue(ex.getMessage().contains("Ambiguous FeatureFlags implementations"));
    }
}
