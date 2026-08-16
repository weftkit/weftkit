package org.weftkit.wiring.processor.validate.element;

import javax.lang.model.element.TypeElement;
import org.weftkit.wiring.StaticHolder;
import org.weftkit.wiring.processor.compiler.Diagnostics;
import org.weftkit.wiring.processor.compiler.Mirrors;
import org.weftkit.wiring.processor.model.ModelCollector;

// Shared base for rules whose annotation references @StaticHolder classes
abstract class HolderRule extends ElementRule<TypeElement> {

    HolderRule(Mirrors mirrors, Diagnostics diagnostics, ModelCollector collector) {
        super(mirrors, diagnostics, collector);
    }

    @SuppressWarnings("deprecation")
    final void requireStaticHolder(TypeElement anchor, String holder) {
        TypeElement element = mirrors.typeElement(holder);
        if (element != null && element.getAnnotation(StaticHolder.class) == null)
            diagnostics.error(anchor, "Holder must be annotated with @StaticHolder: " + holder);
    }
}
