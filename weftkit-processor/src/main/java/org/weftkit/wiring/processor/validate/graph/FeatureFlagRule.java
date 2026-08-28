package org.weftkit.wiring.processor.validate.graph;

import java.util.List;
import javax.lang.model.element.Element;
import org.weftkit.wiring.processor.compiler.Diagnostics;
import org.weftkit.wiring.processor.compiler.Mirrors;
import org.weftkit.wiring.processor.model.WiringModel;

/**
 * Enforces the construction-gating flag contract: nothing may depend on a flagged component, at
 * most one FeatureFlags implementation exists, and the implementation itself is not flagged.
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
            if (!model.components().get(provider).featureFlag().isEmpty())
                diagnostics.error(anchor(provider), "The FeatureFlags implementation itself cannot be feature flagged");
        model.components().forEach((component, definition) -> {
            for (String dependency : model.injectionDependencies(component)) flagFree(component, dependency);
            for (String initializer : model.componentRequirements().getOrDefault(component, List.of()))
                flagFree(component, initializer);
        });
    }

    private void flagFree(String component, String dependency) {
        if (model.components().get(dependency).featureFlag().isEmpty()) return;
        diagnostics.error(
                anchor(component),
                component + " depends on " + dependency
                        + ", which is gated by a feature flag and may never be constructed");
    }

    private Element anchor(String component) {
        return mirrors.typeElement(component);
    }
}
