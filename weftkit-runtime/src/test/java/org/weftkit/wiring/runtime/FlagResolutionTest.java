package org.weftkit.wiring.runtime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.weftkit.wiring.FeatureFlags;
import org.weftkit.wiring.registry.ComponentRegistry;
import org.weftkit.wiring.registry.Dependency;
import org.weftkit.wiring.registry.Flag;
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

    private abstract static class TestRegistry implements ComponentRegistry {

        @Override
        public List<Class<?>> loadOrder() {
            return List.of();
        }

        @Override
        public Set<Class<?>> lazySingletons() {
            return Set.of();
        }

        @Override
        public Map<Class<?>, Map<String, Class<?>>> productOwners() {
            return Map.of();
        }

        @Override
        public Map<Class<?>, Map<String, Function<Object, Object>>> productGetters() {
            return Map.of();
        }

        @Override
        public Map<Class<?>, Map<String, Class<?>>> bindings() {
            return Map.of();
        }
    }

    // A hand-built registry declares no ambient types, so it keeps the permissive matching: a
    // value is injected by its runtime type, and neither an unmatched value nor a null is rejected
    @Test
    void handBuiltRegistryInjectsAmbientValuesByRuntimeType() {
        ComponentRegistry registry = new TestRegistry() {

            @Override
            public Map<Class<?>, List<Dependency>> parameters() {
                return Map.of(TokenUser.class, List.of(new Dependency(Token.class, "", false)));
            }

            @Override
            public Map<Class<?>, Function<Object[], Object>> factories() {
                return Map.of(TokenUser.class, arguments -> new TokenUser((Token) arguments[0]));
            }
        };
        Token token = new Token();
        WeftLoader loader = new WeftLoader(registry, token, null);
        assertSame(token, loader.create(TokenUser.class).token());
    }

    @Test
    void requiredDependencyOnAGatedOffComponentFailsResolution() {
        ComponentRegistry registry = new TestRegistry() {

            @Override
            public Map<Class<?>, List<Dependency>> parameters() {
                return Map.of(
                        Gated.class, List.of(),
                        RequiredUser.class, List.of(new Dependency(Gated.class, "", false)));
            }

            @Override
            public Map<Class<?>, Function<Object[], Object>> factories() {
                return Map.of(
                        Gated.class, arguments -> new Gated(),
                        RequiredUser.class, arguments -> new RequiredUser((Gated) arguments[0]));
            }

            @Override
            public Map<Class<?>, Flag> featureFlags() {
                return Map.of(Gated.class, new Flag("gated", false));
            }
        };
        WeftLoader loader = new WeftLoader(registry);
        ResolutionException ex = assertThrows(ResolutionException.class, () -> loader.create(RequiredUser.class));
        assertTrue(ex.getMessage().contains("gated off by its feature flag"));
    }

    @Test
    void requiredProductOfAGatedOffLazyOwnerIsNotAvailable() {
        Events events = new Events();
        ComponentRegistry registry = new TestRegistry() {

            @Override
            public Set<Class<?>> lazySingletons() {
                return Set.of(GatedProvider.class);
            }

            @Override
            public Map<Class<?>, List<Dependency>> parameters() {
                return Map.of(
                        GatedProvider.class, List.of(),
                        TokenUser.class, List.of(new Dependency(Token.class, "", false)));
            }

            @Override
            public Map<Class<?>, Function<Object[], Object>> factories() {
                return Map.of(
                        GatedProvider.class, arguments -> new GatedProvider(events),
                        TokenUser.class, arguments -> new TokenUser((Token) arguments[0]));
            }

            @Override
            public Map<Class<?>, Map<String, Class<?>>> productOwners() {
                return Map.of(Token.class, Map.of("", GatedProvider.class));
            }

            @Override
            public Map<Class<?>, Map<String, Function<Object, Object>>> productGetters() {
                return Map.of(Token.class, Map.of("", owner -> ((GatedProvider) owner).token()));
            }

            @Override
            public Map<Class<?>, Flag> featureFlags() {
                return Map.of(GatedProvider.class, new Flag("provider", false));
            }
        };
        WeftLoader loader = new WeftLoader(registry);
        ResolutionException ex = assertThrows(ResolutionException.class, () -> loader.create(TokenUser.class));
        assertTrue(ex.getMessage().contains("not available yet"));
        // The gated-off owner was never constructed for its product
        assertTrue(events.log.isEmpty());
    }

    private static ComponentRegistry switchboardRegistry(Events events, boolean enabledByDefault) {
        return new TestRegistry() {

            @Override
            public Set<Class<?>> lazySingletons() {
                return Set.of(Switchboard.class);
            }

            @Override
            public Map<Class<?>, List<Dependency>> parameters() {
                return Map.of(Gated.class, List.of(), Switchboard.class, List.of());
            }

            @Override
            public Map<Class<?>, Function<Object[], Object>> factories() {
                return Map.of(
                        Gated.class, arguments -> new Gated(),
                        Switchboard.class, arguments -> new Switchboard(events));
            }

            @Override
            public Map<Class<?>, Flag> featureFlags() {
                return Map.of(Gated.class, new Flag("gated", enabledByDefault));
            }
        };
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
        ComponentRegistry registry = new TestRegistry() {

            @Override
            public Map<Class<?>, List<Dependency>> parameters() {
                return Map.of(Gated.class, List.of(), Switchboard.class, List.of(), RivalSwitchboard.class, List.of());
            }

            @Override
            public Map<Class<?>, Function<Object[], Object>> factories() {
                return Map.of(
                        Gated.class, arguments -> new Gated(),
                        Switchboard.class, arguments -> new Switchboard(events),
                        RivalSwitchboard.class, arguments -> new RivalSwitchboard());
            }

            @Override
            public Map<Class<?>, Flag> featureFlags() {
                return Map.of(Gated.class, new Flag("gated", false));
            }
        };
        WeftLoader loader = new WeftLoader(registry);
        ResolutionException ex = assertThrows(ResolutionException.class, () -> loader.create(Gated.class));
        assertTrue(ex.getMessage().contains("Ambiguous FeatureFlags implementations"));
    }
}
