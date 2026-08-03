package org.weftkit.wiring.processor;

import java.io.IOException;
import java.io.Writer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;

class GraphGenerator {

    private final WiringModel model;

    private final String registryPackage;

    GraphGenerator(WiringModel model, String registryPackage) {
        this.model = model;
        this.registryPackage = registryPackage;
    }

    void write(ProcessingEnvironment processingEnv) {
        try (Writer writer = processingEnv
                .getFiler()
                .createResource(StandardLocation.SOURCE_OUTPUT, registryPackage, "weftkit-graph.dot")
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

    private String render() {
        StringBuilder graph = new StringBuilder(HEADER);
        model.components()
                .forEach((component, definition) -> graph.append("    \"%s\" [label=\"%s\", tooltip=\"%s\"%s];\n"
                        .formatted(
                                component,
                                simpleName(component),
                                component,
                                definition.singleton() ? SINGLETON_STYLE : "")));
        model.components()
                .forEach((component, definition) -> model.dependencies(component)
                        .forEach(dependency ->
                                graph.append("    \"%s\" -> \"%s\";\n".formatted(component, dependency))));
        return graph.append("}\n").toString();
    }

    private static String simpleName(String qualified) {
        int lastDot = qualified.lastIndexOf('.');
        return lastDot < 0 ? qualified : qualified.substring(lastDot + 1);
    }
}
