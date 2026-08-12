package org.weftkit.wiring.processor.validate.element;

import javax.lang.model.element.TypeElement;
import org.weftkit.wiring.Initializes;
import org.weftkit.wiring.Wired;
import org.weftkit.wiring.processor.compiler.Diagnostics;
import org.weftkit.wiring.processor.compiler.Mirrors;
import org.weftkit.wiring.processor.model.ModelCollector;

// An @Initializes loader owns a holder's setup, so it must run eagerly and exclusively
public final class InitializesRule extends HolderRule {

    public InitializesRule(Mirrors mirrors, Diagnostics diagnostics, ModelCollector collector) {
        super(mirrors, diagnostics, collector);
    }

    @Override
    public void validate(TypeElement component) {
        boolean valid = diagnostics
                .check(component)
                .require(
                        component.getAnnotation(Wired.class) != null && mirrors.isSingleton(component),
                        "@Initializes requires a @Wired singleton")
                .require(!mirrors.isLazy(component), "@Initializes cannot be used on a lazy singleton")
                .passed();
        if (!valid) return;
        for (String holder : mirrors.holders(component.getAnnotation(Initializes.class)::value)) {
            requireStaticHolder(component, holder);
            String previous = collector.addInitializer(holder, component);
            if (previous != null) diagnostics.error(component, "Holder is already initialized by " + previous);
        }
    }
}
