package org.weftkit.wiring.bukkit.metrics;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Opts the annotated plugin out of weftkit's anonymous bStats metrics, see {@link WeftkitMetrics}.
 * Place it on the plugin main next to {@code @Registry}. Only this plugin stops reporting, other
 * weftkit plugins on the server and the plugin's own bStats integration are unaffected.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface NoMetrics {}
