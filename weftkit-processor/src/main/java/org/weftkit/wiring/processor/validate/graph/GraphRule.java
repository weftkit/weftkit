package org.weftkit.wiring.processor.validate.graph;

import org.weftkit.wiring.processor.compiler.Diagnostics;
import org.weftkit.wiring.processor.compiler.Mirrors;
import org.weftkit.wiring.processor.model.WiringModel;

// One whole-graph validation, run once after every element has been validated and collected.
// Adding a case means adding a subclass and listing it in GraphValidator
abstract class GraphRule {

    final WiringModel model;

    final Mirrors mirrors;

    final Diagnostics diagnostics;

    GraphRule(WiringModel model, Mirrors mirrors, Diagnostics diagnostics) {
        this.model = model;
        this.mirrors = mirrors;
        this.diagnostics = diagnostics;
    }

    abstract void validate();
}
