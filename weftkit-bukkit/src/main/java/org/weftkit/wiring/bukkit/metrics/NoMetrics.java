package org.weftkit.wiring.bukkit.metrics;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Opts the annotated plugin out of weftkit's anonymous bStats metrics, see {@link WeftkitMetrics}.
 *
 * @deprecated use {@link WeftMetrics} with {@code enabled = false} instead
 */
@Deprecated(since = "0.4.0", forRemoval = true)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface NoMetrics {}
