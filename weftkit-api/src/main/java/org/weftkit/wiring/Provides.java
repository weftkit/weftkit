package org.weftkit.wiring;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Exposes a singleton's getter as an injectable value. Shared products are captured after load;
 * owned factories create a resource per consuming singleton. Class retention supports incremental processing.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface Provides {

    /** Creates an AutoCloseable once per consuming singleton, closed before that consumer unloads. */
    boolean owned() default false;
}
