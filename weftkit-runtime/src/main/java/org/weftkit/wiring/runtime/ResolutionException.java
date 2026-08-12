package org.weftkit.wiring.runtime;

/**
 * Thrown when the loader cannot resolve what a component needs. The generated registry rules out
 * static wiring mistakes at compile time, so this surfaces the conditions only runtime knows:
 *
 * <ul>
 *   <li>an external dependency whose ambient value was not passed to the loader
 *   <li>an ambient value or call argument matching a parameter type more than once
 *   <li>a singleton requested before {@link WeftLoader#load()} ran or after {@link
 *       WeftLoader#unload()}
 *   <li>a product read before its owner finished loading, or still null afterwards
 *   <li>a class handed to {@link WeftLoader#create} that is not wired
 * </ul>
 *
 * Extends {@link IllegalStateException}, so handlers written against that keep working.
 */
public class ResolutionException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    public ResolutionException(String message) {
        super(message);
    }
}
