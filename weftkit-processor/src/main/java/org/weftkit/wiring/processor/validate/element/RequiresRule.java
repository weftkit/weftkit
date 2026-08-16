package org.weftkit.wiring.processor.validate.element;

import java.util.List;
import javax.lang.model.element.TypeElement;
import org.weftkit.wiring.Requires;
import org.weftkit.wiring.Wired;
import org.weftkit.wiring.processor.compiler.Diagnostics;
import org.weftkit.wiring.processor.compiler.Mirrors;
import org.weftkit.wiring.processor.model.ModelCollector;

// A @Requires component defers to a holder's initializer, ordered after it in the load order or
// materializing it before building
public final class RequiresRule extends HolderRule {

    public RequiresRule(Mirrors mirrors, Diagnostics diagnostics, ModelCollector collector) {
        super(mirrors, diagnostics, collector);
    }

    @Override
    public void validate(TypeElement component) {
        boolean valid = diagnostics
                .check(component)
                .require(component.getAnnotation(Wired.class) != null, "@Requires classes must be @Wired")
                .passed();
        if (!valid) return;
        List<String> holders = mirrors.holders(component.getAnnotation(Requires.class)::value);
        for (String holder : holders) requireStaticHolder(component, holder);
        collector.addRequirements(component, holders);
    }
}
