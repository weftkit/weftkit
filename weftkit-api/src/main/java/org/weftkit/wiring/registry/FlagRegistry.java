package org.weftkit.wiring.registry;

import java.util.Map;

/**
 * The {@code @FeatureFlag} gating data: every flagged component mapped to its flag, letting the
 * runtime gate without an annotation lookup. Defaults to empty for hand-built registries, leaving
 * every component on.
 */
public interface FlagRegistry {

    default Map<Class<?>, Flag> featureFlags() {
        return Map.of();
    }
}
