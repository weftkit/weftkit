package org.weftkit.wiring.processor.validate.graph;

import java.util.List;
import org.weftkit.wiring.processor.compiler.Diagnostics;
import org.weftkit.wiring.processor.compiler.Mirrors;
import org.weftkit.wiring.processor.model.WiringModel;

public final class GraphValidator {

    private final List<GraphRule> rules;

    // DependencyRule records the bindings the later rules and the generators read, so it runs
    // first, and CycleRule walks the resolved graph, so it runs last
    public GraphValidator(WiringModel model, Mirrors mirrors, Diagnostics diagnostics) {
        this.rules = List.of(
                new DependencyRule(model, mirrors, diagnostics),
                new ProductRule(model, mirrors, diagnostics),
                new InitializerRule(model, mirrors, diagnostics),
                new RequirementRule(model, mirrors, diagnostics),
                new CycleRule(model, mirrors, diagnostics));
    }

    public void validate() {
        for (GraphRule rule : rules) rule.validate();
    }
}
