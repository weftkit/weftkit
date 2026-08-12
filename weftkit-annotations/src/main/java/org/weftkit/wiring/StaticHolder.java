package org.weftkit.wiring;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class holding static state that components read directly instead of being injected.
 * Static state is invisible to the dependency graph, so the holder annotations turn it into
 * explicit load order: one {@link Initializes} loader fills the holder, {@link Requires} readers
 * load after it, and the processor flags a component that accesses a holder during construction
 * or load without declaring {@link Requires}. Class retention is required for incremental
 * annotation processing.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface StaticHolder {}
