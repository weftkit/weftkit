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

    private String render() {
        StringBuilder graph = new StringBuilder("digraph weftkit {\n");
        model.components().forEach((component, definition) -> {
            graph.append("    \"%s\"%s;\n".formatted(component, definition.singleton() ? " [shape=box]" : ""));
            for (String dependency : model.dependencies(component))
                graph.append("    \"%s\" -> \"%s\";\n".formatted(component, dependency));
        });
        return graph.append("}\n").toString();
    }
}
