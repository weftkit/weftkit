package org.weftkit.wiring.runtime;

import org.weftkit.wiring.FeatureFlag;

/** The pure feature-flag decision, split out so it holds no graph state and tests without one. */
final class FeatureGate {

    private FeatureGate() {}

    /**
     * Whether components of the type are enabled. A type without {@link FeatureFlag} is always on.
     * With the annotation, a present {@code flags} decides by key. A null {@code flags} (no
     * implementation resolvable) falls back to {@link FeatureFlag#enabledByDefault}.
     */
    static boolean enabled(Class<?> type, FeatureFlags flags) {
        FeatureFlag flag = type.getAnnotation(FeatureFlag.class);
        if (flag == null) return true;
        return flags == null ? flag.enabledByDefault() : flags.isEnabled(flag.value());
    }
}
