package org.weftkit.wiring.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RegistriesTest {

    @Test
    void mergeCombinesAllContributions() {
        Map<Class<?>, String> merged =
                Registries.merge(Map.of(String.class, "a"), Map.of(Integer.class, "b"), Map.of(Long.class, "c"));
        assertEquals(Map.of(String.class, "a", Integer.class, "b", Long.class, "c"), merged);
        assertThrows(UnsupportedOperationException.class, () -> merged.put(Double.class, "d"));
    }

    @Test
    void mergeNestedCombinesPerOuterKey() {
        Map<Class<?>, Map<String, String>> merged = Registries.mergeNested(
                Map.of(String.class, Map.of("", "central")),
                Map.of(String.class, Map.of("backup", "fragment"), Integer.class, Map.of("", "other")));
        assertEquals(Map.of("", "central", "backup", "fragment"), merged.get(String.class));
        assertEquals(Map.of("", "other"), merged.get(Integer.class));
    }

    @Test
    void unionCombinesAllContributions() {
        Set<Class<?>> merged = Registries.union(Set.of(String.class), Set.of(Integer.class));
        assertEquals(Set.of(String.class, Integer.class), merged);
        assertTrue(merged.contains(Integer.class));
    }

    @Test
    void typeResolvesByBinaryName() {
        Map<Class<?>, String> components = Map.of(String.class, "", Integer.class, "");
        assertSame(Integer.class, Registries.type(components, "java.lang.Integer"));
    }

    @Test
    void typeFailsFastOnUnknownName() {
        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> Registries.type(Map.of(), "com.example.Missing"));
        assertEquals("Stale weftkit registry, run a clean build: com.example.Missing", ex.getMessage());
    }
}
