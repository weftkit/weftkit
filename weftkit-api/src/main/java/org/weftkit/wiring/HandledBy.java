package org.weftkit.wiring;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Names the {@link WeftModule} that reads a runtime component annotation. Components carrying the
 * annotation stay inert while that module is missing from the registry, which adapters warn
 * about at enable. Carrying it while the registry disables that module fails the build. The
 * annotation and every type its values name must be public, and repeated annotations are recorded
 * one by one.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.ANNOTATION_TYPE)
public @interface HandledBy {

    Class<?> value();
}
