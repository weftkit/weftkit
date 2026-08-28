package org.weftkit.wiring;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Gates a wired component behind a named feature flag. An off flag gates construction: the
 * component is never created, its lifecycle hooks never run, and module enumeration skips it.
 * Resolved against a {@code FeatureFlags} implementation (ambient or wired); without one,
 * {@link #enabledByDefault} decides. Because nothing may depend on a flagged component, flags fit
 * leaf features like listeners and commands. Runtime retention, read while the server runs.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface FeatureFlag {

    String value();

    boolean enabledByDefault() default true;
}
