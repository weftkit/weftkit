package org.weftkit.wiring;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the single class that drives the wiring at runtime. The component registry is generated
 * into its package, and its constructor parameters are available to every component. Class
 * retention is required for incremental annotation processing.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface Registry {

    /**
     * {@link WeftModule} classes to weave into the generated registry as hard references. Bundled
     * adapter modules (listener wiring, metrics) need no declaration.
     */
    Class<?>[] modules() default {};

    /**
     * {@link WeftModule#id() Ids} of modules to keep out of this plugin, through an id holder like
     * {@code BukkitModules.METRICS}, which imports no module class. A disabled module and its
     * components are absent from the generated wiring. An unknown id fails the build.
     */
    String[] disable() default {};

    /**
     * Extra ambient types beyond the registry class and its constructor parameters, for values
     * handed to the loader at runtime, e.g. from {@code ambientValues()}. Ambient resolution is
     * declaration based: a value matching no declared type is rejected when the loader is created,
     * and an undeclared dependency fails the build instead of the server.
     */
    Class<?>[] ambient() default {};
}
