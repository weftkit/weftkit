package org.weftkit.wiring.bukkit.metrics;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Configures weftkit's anonymous bStats metrics for the annotated plugin, see
 * {@link WeftkitMetrics}. Place it on the plugin main next to {@code @Registry}. Without the
 * annotation the defaults apply - metrics run, the plugin's name is not reported.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface WeftMetrics {

    /**
     * Whether weftkit's metrics run for this plugin at all. Disabling only affects this plugin -
     * other weftkit plugins on the server and this plugin's own bStats integration are unaffected.
     */
    boolean enabled() default true;

    /**
     * Whether this plugin's name appears on weftkit's public plugins chart. Off by default so
     * private plugin names never leave the server. Ignored when {@link #enabled()} is false.
     */
    boolean reportName() default false;
}
