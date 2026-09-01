package org.weftkit.wiring.runtime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.weftkit.wiring.runtime.exception.ResolutionException;
import org.weftkit.wiring.runtime.registry.AmbientRegistry;

/**
 * The ambient values, one slot per declared type: a value may fill every slot it matches, a slot
 * never holds two values. Sees only the {@link AmbientRegistry}. An empty declaration marks a
 * hand-built registry and keeps the permissive matching: each value declares its own runtime type.
 */
final class AmbientValues {

    // Declared ambient type to value, null for a declared type without a value
    private final Map<Class<?>, Object> slots = new LinkedHashMap<>();

    AmbientValues(AmbientRegistry registry, Object[] values) {
        List<Class<?>> declared = registry.ambientTypes();
        boolean declarationBased = !declared.isEmpty();
        for (Class<?> type : declared) slots.putIfAbsent(type, null);
        slots.putIfAbsent(FeatureFlags.class, null);
        for (Object value : values) {
            // A null is an absent value, like a declared type left unfilled, not an error
            if (value == null) continue;
            // Hand-built registries declare nothing, so a value stands for its own runtime type
            if (!declarationBased) slots.putIfAbsent(value.getClass(), null);
            fill(value);
        }
    }

    private void fill(Object value) {
        boolean matched = false;
        for (Map.Entry<Class<?>, Object> slot : slots.entrySet()) {
            if (!slot.getKey().isInstance(value)) continue;
            if (slot.getValue() != null && slot.getValue() != value)
                throw new ResolutionException("Ambiguous ambient value for declared type "
                        + slot.getKey().getName() + ": "
                        + slot.getValue().getClass().getName()
                        + " and " + value.getClass().getName() + " both match");
            slot.setValue(value);
            matched = true;
        }
        if (!matched)
            throw new ResolutionException(
                    "Ambient value of type " + value.getClass().getName()
                            + " matches no declared ambient type. Declare it on the @Registry class");
    }

    /**
     * The value of the declared type assignable to the type, or null when none matches or the
     * slot is empty. Throws when several matching declared types hold different values.
     */
    Object match(Class<?> type) {
        Object match = null;
        for (Map.Entry<Class<?>, Object> slot : slots.entrySet()) {
            if (!type.isAssignableFrom(slot.getKey()) || slot.getValue() == null) continue;
            if (match != null && match != slot.getValue())
                throw new ResolutionException("Ambiguous ambient value for " + type.getName());
            match = slot.getValue();
        }
        return match;
    }

    /** Whether a declared type is assignable to the type, its slot filled or not. */
    boolean declared(Class<?> type) {
        for (Class<?> declared : slots.keySet()) if (type.isAssignableFrom(declared)) return true;
        return false;
    }
}
