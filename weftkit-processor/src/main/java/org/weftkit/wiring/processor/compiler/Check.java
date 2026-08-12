package org.weftkit.wiring.processor.compiler;

import javax.lang.model.element.Element;

// Replaces a ladder of early returns: only the first failed requirement is reported and later
// failures stay silent. Conditions are still evaluated, so they must hold without side effects
// on any element that reached the check
public final class Check {

    private final Diagnostics diagnostics;

    private final Element element;

    private boolean failed;

    Check(Diagnostics diagnostics, Element element) {
        this.diagnostics = diagnostics;
        this.element = element;
    }

    public Check require(boolean condition, String message) {
        if (!failed && !condition) {
            diagnostics.error(element, message);
            failed = true;
        }
        return this;
    }

    public boolean passed() {
        return !failed;
    }
}
