package org.weftkit.wiring.processor.validate.element;

import javax.lang.model.element.Element;
import org.weftkit.wiring.processor.compiler.Diagnostics;
import org.weftkit.wiring.processor.compiler.Mirrors;
import org.weftkit.wiring.processor.model.ModelCollector;

// One validation of a single annotated element, run as the processor visits it. Adding a case
// means adding a subclass and wiring it into WiredValidator
abstract class ElementRule<E extends Element> {

    final Mirrors mirrors;

    final Diagnostics diagnostics;

    final ModelCollector collector;

    ElementRule(Mirrors mirrors, Diagnostics diagnostics, ModelCollector collector) {
        this.mirrors = mirrors;
        this.diagnostics = diagnostics;
        this.collector = collector;
    }

    abstract void validate(E element);
}
