package org.weftkit.wiring;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Distinguishes multiple dependencies of the same type. On a wired class or a {@link Provides}
 * getter it tags what is offered, and on a constructor parameter it selects the implementation or
 * product carrying that tag. On a field the annotation is inert to the processor. It exists there
 * so constructor generators such as Lombok ({@code lombok.copyableAnnotations +=
 * org.weftkit.wiring.Qualified}) can copy it onto the generated constructor parameter. Class
 * retention is required for incremental annotation processing.
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD})
public @interface Qualified {

    String value();
}
