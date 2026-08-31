package org.weftkit.wiring.processor.validate.graph;

import java.util.List;
import javax.lang.model.element.Element;
import org.weftkit.wiring.processor.compiler.Diagnostics;
import org.weftkit.wiring.processor.compiler.Mirrors;
import org.weftkit.wiring.processor.model.WiringModel;

/**
 * Enforces the construction-gating flag contract: nothing may <em>require</em> a flagged component
 * (an {@code Optional<T>} dependency is allowed and resolves empty when the flag is off), at most
 * one FeatureFlags implementation exists, and the implementation itself is not flagged.
 */
final class FeatureFlagRule extends GraphRule {

    FeatureFlagRule(WiringModel model, Mirrors mirrors, Diagnostics diagnostics) {
        super(model, mirrors, diagnostics);
    }

    @Override
    void validate() {
        if (model.flagsProviders().size() > 1)
            diagnostics.error("Several FeatureFlags implementations are wired, keep one: "
                    + String.join(", ", model.flagsProviders()));
        for (String provider : model.flagsProviders())
            if (model.components().get(provider).featureFlag() != null)
                diagnostics.error(anchor(provider), "The FeatureFlags implementation itself cannot be feature flagged");
        model.components().forEach((component, definition) -> {
            for (String dependency : model.requiredInjectionDependencies(component)) flagFree(component, dependency);
            for (String initializer : model.componentRequirements().getOrDefault(component, List.of()))
                flagFree(component, initializer);
        });
    }

    private void flagFree(String component, String dependency) {
        if (model.components().get(dependency).featureFlag() == null) return;
        diagnostics.error(
                anchor(component),
                component + " requires " + dependency
                        + ", which is gated by a feature flag and may never be constructed; depend on it as"
                        + " Optional<" + dependency + "> to allow it to be absent when the flag is off");
    }

    private Element anchor(String component) {
        return mirrors.typeElement(component);
    }
}
