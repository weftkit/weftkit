package org.weftkit.wiring.processor;

import java.io.IOException;
import java.io.Writer;
import java.util.Set;
import java.util.TreeSet;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;

class GraphGenerator {

    private final WiringModel model;

    private final String registryPackage;

    GraphGenerator(WiringModel model, String registryPackage) {
        this.model = model;
        this.registryPackage = registryPackage;
    }

    void write(ProcessingEnvironment processingEnv, Element... originating) {
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

    private static final String HOLDER_STYLE =
            ", style=\"rounded,filled,dashed\", fillcolor=\"#F5F5F4\", color=\"#78716C\"";

    private String render() {
        StringBuilder graph = new StringBuilder(HEADER);
        model.components()
                .forEach((component, definition) -> graph.append("    \"%s\" [label=\"%s\", tooltip=\"%s\"%s];\n"
                        .formatted(
                                component,
                                simpleName(component),
                                component,
                                definition.singleton() ? SINGLETON_STYLE : "")));
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
}
