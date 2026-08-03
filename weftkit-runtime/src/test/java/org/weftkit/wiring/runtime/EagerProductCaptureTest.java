package org.weftkit.wiring.runtime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.weftkit.wiring.Loader;

class EagerProductCaptureTest {

    public static final class Blank {}

    public static final class Forgetful implements Loader {

        @Override
        public boolean load() {
            return true;
        }

        public Blank blank() {
            return null;
        }
    }

    private static final ComponentRegistry REGISTRY = new ComponentRegistry() {

        @Override
        public List<Class<?>> loadOrder() {
            return List.of(Forgetful.class);
        }

        @Override
        public Set<Class<?>> lazySingletons() {
            return Set.of();
        }

        @Override
        public Map<Class<?>, List<Dependency>> parameters() {
            return Map.of(Forgetful.class, List.of());
        }

        @Override
        public Map<Class<?>, Function<Object[], Object>> factories() {
            return Map.of(Forgetful.class, arguments -> new Forgetful());
        }

        @Override
        public Map<Class<?>, Map<String, Class<?>>> productOwners() {
            return Map.of(Blank.class, Map.of("", Forgetful.class));
        }

        @Override
        public Map<Class<?>, Map<String, Function<Object, Object>>> productGetters() {
            return Map.of(Blank.class, Map.of("", owner -> ((Forgetful) owner).blank()));
        }

        @Override
        public Map<Class<?>, Map<String, Class<?>>> bindings() {
            return Map.of();
        }
    };

    @Test
    void failsLoadWhenAProductIsNullAfterItsOwnerLoaded() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, new WeftLoader(REGISTRY)::load);
        assertTrue(ex.getMessage().contains("null after load"));
    }
}
