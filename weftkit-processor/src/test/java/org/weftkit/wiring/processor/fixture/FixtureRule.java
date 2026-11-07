package org.weftkit.wiring.processor.fixture;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import org.weftkit.wiring.processor.spi.ComponentRule;

/** Exercises an adapter error reported through the SPI messager. */
public final class FixtureRule implements ComponentRule {
    @Override
    public void validate(TypeElement component, ProcessingEnvironment environment) {
        if (component.getSimpleName().contentEquals("RuleError"))
            error(environment, component, "Fixture adapter rejected this component");
    }
}
