package org.weftkit.wiring;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a wired component as created once and injected by type from then on, where a plain
 * component is created fresh for every injection. An eager singleton is created during load, a
 * lazy one on its first injection. Class retention is required for incremental annotation
 * processing.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface Singleton {

    boolean lazy() default false;
}
