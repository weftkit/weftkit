package org.weftkit.wiring.processor;

import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;

final class Diagnostics {

    private final Messager messager;

    Diagnostics(ProcessingEnvironment processingEnv) {
        this.messager = processingEnv.getMessager();
    }

    void error(String message) {
        messager.printMessage(Diagnostic.Kind.ERROR, message);
    }

    void error(Element element, String message) {
        messager.printMessage(Diagnostic.Kind.ERROR, message, element);
    }

    void warning(Element element, String message) {
        messager.printMessage(Diagnostic.Kind.WARNING, message, element);
    }
}
