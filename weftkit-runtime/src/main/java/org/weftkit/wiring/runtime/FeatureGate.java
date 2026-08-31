package org.weftkit.wiring.runtime;

import org.weftkit.wiring.runtime.registry.Flag;

/** The pure feature-flag decision, split out so it holds no graph state and tests without one. */
final class FeatureGate {

    private FeatureGate() {}

    /**
     * Whether components carrying the flag are enabled. A null {@code flag} (unflagged component)
     * is always on. A present {@code flags} decides by key. A null {@code flags} (no
     * implementation resolvable) falls back to {@link Flag#enabledByDefault}.
     */
    static boolean enabled(Flag flag, FeatureFlags flags) {
        if (flag == null) return true;
        return flags == null ? flag.enabledByDefault() : flags.isEnabled(flag.key());
    }
}
