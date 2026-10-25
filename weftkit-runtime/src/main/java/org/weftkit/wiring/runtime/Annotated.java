package org.weftkit.wiring.runtime;

import java.lang.annotation.Annotation;

/** A component paired with one {@code @HandledBy} annotation its class carries, see {@link Weft#annotated}. */
public record Annotated<T, A extends Annotation>(T component, A annotation) {}
