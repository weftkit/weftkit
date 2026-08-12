package org.weftkit.wiring.processor.validate.element;

import javax.lang.model.element.TypeElement;
import org.weftkit.wiring.Wired;
import org.weftkit.wiring.processor.compiler.Diagnostics;
import org.weftkit.wiring.processor.compiler.Mirrors;
import org.weftkit.wiring.processor.model.ModelCollector;

// @Singleton only shapes how a @Wired component is created, it wires nothing on its own
public final class SingletonRule extends ElementRule<TypeElement> {

    public SingletonRule(Mirrors mirrors, Diagnostics diagnostics, ModelCollector collector) {
        super(mirrors, diagnostics, collector);
    }

    @Override
    public void validate(TypeElement component) {
        if (component.getAnnotation(Wired.class) == null)
            diagnostics.error(component, "@Singleton classes must be @Wired");
    }
}
