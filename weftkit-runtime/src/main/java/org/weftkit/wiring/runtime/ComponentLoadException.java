package org.weftkit.wiring.runtime;

/** Thrown when a component constructor fails during creation. */
public class ComponentLoadException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ComponentLoadException(String component, Throwable cause) {
        super("Failed to load component " + component, cause);
    }
}
