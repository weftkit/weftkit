package org.weftkit.wiring;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Gates a wired component behind a named feature flag. An off flag gates construction: the
 * component is never created, its lifecycle hooks never run, and module enumeration skips it.
 * Resolved against a {@code FeatureFlags} implementation (ambient or wired); without one,
 * {@link #enabledByDefault} decides. Nothing may <em>require</em> a flagged component, so flags fit
 * leaf features like listeners and commands. A consumer that should tolerate its absence depends on
 * it as {@code Optional<T>}, which resolves empty while the flag is off. The generated registry
 * records key and default, so gating needs no runtime reflection. Runtime retention only keeps
 * the flag introspectable for plugins.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface FeatureFlag {

    String value();

    boolean enabledByDefault() default true;
}
