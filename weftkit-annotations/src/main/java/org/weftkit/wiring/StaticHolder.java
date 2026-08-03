package org.weftkit.wiring;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class whose static state is set up by an {@link Initializes initializing} loader.
 * {@link Initializes} and {@link Requires} only accept classes carrying this annotation, and the
 * processor flags components that access a holder during construction or load without declaring
 * {@link Requires}. Class retention is required for incremental annotation processing.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface StaticHolder {}
