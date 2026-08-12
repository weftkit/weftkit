package org.weftkit.wiring.processor.validate.graph;

import javax.lang.model.element.Element;
import org.weftkit.wiring.processor.compiler.Diagnostics;
import org.weftkit.wiring.processor.compiler.Mirrors;
import org.weftkit.wiring.processor.model.WiringModel;

// An initialized holder must be plain static state, not something the loader already manages
final class InitializerRule extends GraphRule {

    InitializerRule(WiringModel model, Mirrors mirrors, Diagnostics diagnostics) {
        super(model, mirrors, diagnostics);
    }

    @Override
    void validate() {
        model.initializers().forEach((holder, initializer) -> {
            Element anchor = mirrors.typeElement(initializer);
            if (model.isComponent(holder))
                diagnostics.error(anchor, "Initialized holder is also a @Wired component: " + holder);
            else if (model.isProductType(holder))
                diagnostics.error(anchor, "Initialized holder is also a @Provides product: " + holder);
        });
    }
}
