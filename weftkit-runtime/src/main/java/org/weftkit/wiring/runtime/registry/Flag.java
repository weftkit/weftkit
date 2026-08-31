package org.weftkit.wiring.runtime.registry;

/**
 * A component's feature flag as recorded in the registry: the key resolved against a
 * {@code FeatureFlags} implementation, and the fallback when none resolves.
 */
public record Flag(String key, boolean enabledByDefault) {}
