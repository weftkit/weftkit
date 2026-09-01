package org.weftkit.wiring.processor.validate.graph;

import java.util.List;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
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

    final List<? extends VariableElement> constructorParameters(TypeElement component) {
        List<ExecutableElement> constructors = mirrors.accessibleConstructors(component);
        return constructors.size() == 1 ? constructors.get(0).getParameters() : List.of();
    }

    final Element anchor(TypeElement component, List<? extends VariableElement> parameters, int index) {
        return index >= 0 && index < parameters.size() ? parameters.get(index) : component;
    }
}
