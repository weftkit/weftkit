package org.weftkit.wiring;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Distinguishes multiple dependencies of the same type. On a wired class or a {@link Provides}
 * getter it tags what is offered, and on a constructor parameter it selects the implementation or
 * product carrying that tag. Class retention is required for incremental annotation processing.
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.PARAMETER})
public @interface Qualified {

    String value();
}
