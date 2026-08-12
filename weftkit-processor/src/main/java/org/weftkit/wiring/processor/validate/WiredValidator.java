package org.weftkit.wiring.processor.validate;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import org.weftkit.wiring.processor.compiler.Diagnostics;
import org.weftkit.wiring.processor.compiler.Mirrors;
import org.weftkit.wiring.processor.model.ModelCollector;
import org.weftkit.wiring.processor.model.WiringModel;
import org.weftkit.wiring.processor.validate.element.InitializesRule;
import org.weftkit.wiring.processor.validate.element.ProvidesRule;
import org.weftkit.wiring.processor.validate.element.RegistryRule;
import org.weftkit.wiring.processor.validate.element.RequiresRule;
import org.weftkit.wiring.processor.validate.element.SingletonRule;
import org.weftkit.wiring.processor.validate.element.WiredRule;
import org.weftkit.wiring.processor.validate.graph.GraphValidator;

/** Routes every annotated element to its rule, then runs the whole-graph rules. */
public class WiredValidator {

    private final Diagnostics diagnostics;

    private final ModelCollector collector;

    private final RegistryRule registryRule;

    private final WiredRule wiredRule;

    private final SingletonRule singletonRule;

    private final ProvidesRule providesRule;

    private final InitializesRule initializesRule;

    private final RequiresRule requiresRule;

    private final GraphValidator graph;

    public WiredValidator(ProcessingEnvironment processingEnv, WiringModel model) {
        Mirrors mirrors = new Mirrors(processingEnv);
        this.diagnostics = new Diagnostics(processingEnv);
        this.collector = new ModelCollector(model, mirrors, diagnostics);
        this.registryRule = new RegistryRule(mirrors, diagnostics, collector);
        this.wiredRule = new WiredRule(processingEnv, mirrors, diagnostics, collector);
        this.singletonRule = new SingletonRule(mirrors, diagnostics, collector);
        this.providesRule = new ProvidesRule(mirrors, diagnostics, collector);
        this.initializesRule = new InitializesRule(mirrors, diagnostics, collector);
        this.requiresRule = new RequiresRule(mirrors, diagnostics, collector);
        this.graph = new GraphValidator(model, mirrors, diagnostics);
    }

    public void addSources(RoundEnvironment roundEnvironment) {
        collector.addSources(roundEnvironment);
    }

    public String registryPackage() {
        return collector.registryPackage();
    }

    public String registryClass() {
        return collector.registryClass();
    }

    public void validateRegistry(TypeElement element) {
        registryRule.validate(element);
    }

    public void validateComponent(TypeElement component) {
        wiredRule.validate(component);
    }

    public void validateSingleton(TypeElement component) {
        singletonRule.validate(component);
    }

    public void validateProduct(ExecutableElement getter) {
        providesRule.validate(getter);
    }

    public void validateInitializes(TypeElement component) {
        initializesRule.validate(component);
    }

    public void validateRequires(TypeElement component) {
        requiresRule.validate(component);
    }

    public void validateGraph() {
        if (collector.registryClass() == null)
            diagnostics.error("No @Registry class to generate the component registry for");
        graph.validate();
    }
}
