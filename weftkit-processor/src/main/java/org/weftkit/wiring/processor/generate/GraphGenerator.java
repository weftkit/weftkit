package org.weftkit.wiring.processor.generate;

import java.io.IOException;
import java.io.Writer;
import java.util.Set;
import java.util.TreeSet;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;
import org.weftkit.wiring.processor.model.WiringModel;

public class GraphGenerator {

    private final WiringModel model;

    private final String registryPackage;

    public GraphGenerator(WiringModel model, String registryPackage) {
        this.model = model;
        this.registryPackage = registryPackage;
    }

    public void write(ProcessingEnvironment processingEnv, Element... originating) {
        try (Writer writer = processingEnv
                .getFiler()
                .createResource(StandardLocation.SOURCE_OUTPUT, registryPackage, "weftkit-graph.dot", originating)
                .openWriter()) {
            writer.write(render());
        } catch (IOException ex) {
            processingEnv
                    .getMessager()
                    .printMessage(Diagnostic.Kind.ERROR, "Failed to generate dependency graph: " + ex.getMessage());
        }
    }

    private static final String HEADER = """
            digraph weftkit {
                rankdir=LR;
                graph [ranksep=1.1, nodesep=0.28, splines=spline, concentrate=true, bgcolor=transparent];
                node [shape=box, style="rounded,filled", fontname="Helvetica", fontsize=11, \
            margin="0.16,0.08", color="#4F46E5", fillcolor="#EEF0FB", fontcolor="#1E1B2E", penwidth=1.2];
                edge [color="#9AA0B4", arrowsize=0.7, penwidth=1.0];
            """;

    private static final String SINGLETON_STYLE = ", fillcolor=\"#FFF7ED\", color=\"#F59E0B\", penwidth=1.8";

    private static final String MODULE_STYLE = ", fillcolor=\"#ECFDF5\", color=\"#0D9488\", penwidth=1.8";

    private static final String HIDDEN_STYLE = ", style=\"rounded,filled,dashed\"";

    private static final String HOLDER_STYLE =
            ", style=\"rounded,filled,dashed\", fillcolor=\"#F5F5F4\", color=\"#78716C\"";

    private static final String FLAG_COLOR = "#B45309";

    private String render() {
        StringBuilder graph = new StringBuilder(HEADER);
        model.components()
                .forEach((component, definition) -> graph.append("    \"%s\" [%s, tooltip=\"%s\"%s%s];\n"
                        .formatted(
                                component,
                                label(simpleName(component), definition.featureFlag()),
                                tooltip(component, definition.featureFlag())
                                        + (model.isModule(component) ? " (module)" : ""),
                                model.isModule(component)
                                        ? MODULE_STYLE
                                        : definition.singleton() ? SINGLETON_STYLE : "",
                                model.isHidden(component) ? HIDDEN_STYLE : "")));
        holders()
                .forEach(holder -> graph.append("    \"%s\" [label=\"%s\", tooltip=\"%s\"%s];\n"
                        .formatted(holder, simpleName(holder), holder, HOLDER_STYLE)));
        model.components()
                .forEach((component, definition) -> model.injectionDependencies(component)
                        .forEach(dependency ->
                                graph.append("    \"%s\" -> \"%s\";\n".formatted(component, dependency))));
        model.requirements()
                .forEach((component, required) -> required.forEach(
                        holder -> graph.append("    \"%s\" -> \"%s\" [style=dashed];\n".formatted(component, holder))));
        model.initializers()
                .forEach((holder, initializer) -> graph.append(
                        "    \"%s\" -> \"%s\" [style=dashed, color=\"#F59E0B\"];\n".formatted(holder, initializer)));
        return graph.append("}\n").toString();
    }

    private Set<String> holders() {
        Set<String> holders = new TreeSet<>(model.initializers().keySet());
        model.requirements().values().forEach(holders::addAll);
        return holders;
    }

    private static String simpleName(String qualified) {
        int lastDot = qualified.lastIndexOf('.');
        return lastDot < 0 ? qualified : qualified.substring(lastDot + 1);
    }

    private static String label(String name, String featureFlag) {
        if (featureFlag.isEmpty()) return "label=\"" + name + "\"";
        return "label=<%s<br/><font point-size=\"9\" color=\"%s\">feature: %s</font>>"
                .formatted(escapeHtml(name), FLAG_COLOR, escapeHtml(featureFlag));
    }

    private static String tooltip(String component, String featureFlag) {
        return featureFlag.isEmpty() ? component : component + " (feature: " + featureFlag + ")";
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
