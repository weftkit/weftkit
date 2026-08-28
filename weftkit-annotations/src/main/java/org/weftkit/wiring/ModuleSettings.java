package org.weftkit.wiring;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an interface as consumer configuration for a {@link WeftModule}, injected as a plain
 * constructor parameter. A plugin optionally wires an implementation; without one, the processor
 * generates an implementation keeping every method default, so the module never checks for
 * absence. Every method must have a default, or the consumer's build fails until an
 * implementation is wired.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface ModuleSettings {}
