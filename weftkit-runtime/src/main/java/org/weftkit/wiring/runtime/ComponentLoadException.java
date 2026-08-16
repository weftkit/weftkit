package org.weftkit.wiring.runtime;

/**
 * Thrown when a component constructor fails during creation, or when the load hook of a lazy
 * singleton aborts its materialization.
 */
public class ComponentLoadException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ComponentLoadException(String component, Throwable cause) {
        super("Failed to load component " + component, cause);
    }

    public ComponentLoadException(String component) {
        super("Load hook aborted for component " + component);
    }
}
