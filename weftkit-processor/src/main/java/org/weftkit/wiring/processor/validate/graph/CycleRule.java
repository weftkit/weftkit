package org.weftkit.wiring.processor.validate.graph;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.weftkit.wiring.processor.compiler.Diagnostics;
import org.weftkit.wiring.processor.compiler.Mirrors;
import org.weftkit.wiring.processor.model.WiringModel;

// Rejects dependency cycles. Runs on the resolved graph, so it must come after DependencyRule
final class CycleRule extends GraphRule {

    CycleRule(WiringModel model, Mirrors mirrors, Diagnostics diagnostics) {
        super(model, mirrors, diagnostics);
    }

    @Override
    void validate() {
        Set<String> cleared = new HashSet<>();
        for (String component : model.components().keySet()) check(component, new ArrayList<>(), cleared);
    }

    // cleared holds nodes whose whole subgraph is proven acyclic, so shared subgraphs are walked
    // once. A cycle member can never be cleared without its back edge first being caught by the
    // path check
    private void check(String component, List<String> path, Set<String> cleared) {
        if (cleared.contains(component)) return;
        if (path.contains(component)) {
            // The walk may have entered the cycle through an acyclic prefix, so the path is
            // trimmed to the first cycle member and the message names only actual members
            List<String> cycle = path.subList(path.indexOf(component), path.size());
            diagnostics.error(
                    mirrors.typeElement(component),
                    "Component dependency cycle: " + String.join(" -> ", cycle) + " -> " + component);
            return;
        }
        path.add(component);
        for (String dependency : model.dependencies(component)) check(dependency, path, cleared);
        path.remove(path.size() - 1);
        cleared.add(component);
    }
}
