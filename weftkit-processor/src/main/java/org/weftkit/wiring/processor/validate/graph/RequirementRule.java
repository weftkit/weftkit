package org.weftkit.wiring.processor.validate.graph;

import javax.lang.model.element.Element;
import org.weftkit.wiring.processor.compiler.Diagnostics;
import org.weftkit.wiring.processor.compiler.Mirrors;
import org.weftkit.wiring.processor.model.WiringModel;

// Every @Requires holder needs a loader that @Initializes it, or the load order cannot honor it
final class RequirementRule extends GraphRule {

    RequirementRule(WiringModel model, Mirrors mirrors, Diagnostics diagnostics) {
        super(model, mirrors, diagnostics);
    }

    @Override
    void validate() {
        model.requirements().forEach((component, holders) -> {
            Element anchor = mirrors.typeElement(component);
            for (String holder : holders) {
                if (!model.isInitialized(holder)) diagnostics.error(anchor, "No @Wired loader initializes: " + holder);
            }
        });
    }
}
