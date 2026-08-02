package org.weftkit.wiring.runtime;

import java.util.Objects;

/**
 * A constructor parameter of a wired component as recorded in the generated registry. The
 * qualifier is empty for unqualified parameters, and optional marks {@code Optional} parameters
 * that resolve to empty instead of failing.
 */
public record Dependency(Class<?> type, String qualifier, boolean optional) {

    public Dependency {
        Objects.requireNonNull(type);
        Objects.requireNonNull(qualifier);
    }
}
