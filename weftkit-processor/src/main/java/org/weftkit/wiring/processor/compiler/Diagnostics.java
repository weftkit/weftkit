package org.weftkit.wiring.processor.compiler;

import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;

public final class Diagnostics {

    private final Messager messager;

    public Diagnostics(ProcessingEnvironment processingEnv) {
        this.messager = processingEnv.getMessager();
    }

    public void error(String message) {
        messager.printMessage(Diagnostic.Kind.ERROR, message);
    }

    public void error(Element element, String message) {
        messager.printMessage(Diagnostic.Kind.ERROR, message, element);
    }

    public void warning(Element element, String message) {
        messager.printMessage(Diagnostic.Kind.WARNING, message, element);
    }

    public Check check(Element element) {
        return new Check(this, element);
    }
}
