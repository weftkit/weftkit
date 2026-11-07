package org.weftkit.wiring.processor.compiler;

import java.util.Locale;
import java.util.Map;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

/** Tracks errors reported by adapter rules without changing their SPI. */
public final class ValidationEnvironment implements ProcessingEnvironment {
    private final ProcessingEnvironment delegate;
    private final Messager messager;

    public ValidationEnvironment(ProcessingEnvironment delegate, Diagnostics diagnostics) {
        this.delegate = delegate;
        Messager original = delegate.getMessager();
        this.messager = new Messager() {
            private void track(Diagnostic.Kind kind) {
                if (kind == Diagnostic.Kind.ERROR) diagnostics.markError();
            }

            @Override
            public void printMessage(Diagnostic.Kind kind, CharSequence message) {
                track(kind);
                original.printMessage(kind, message);
            }

            @Override
            public void printMessage(Diagnostic.Kind kind, CharSequence message, Element element) {
                track(kind);
                original.printMessage(kind, message, element);
            }

            @Override
            public void printMessage(
                    Diagnostic.Kind kind, CharSequence message, Element element, AnnotationMirror annotation) {
                track(kind);
                original.printMessage(kind, message, element, annotation);
            }

            @Override
            public void printMessage(
                    Diagnostic.Kind kind,
                    CharSequence message,
                    Element element,
                    AnnotationMirror annotation,
                    AnnotationValue value) {
                track(kind);
                original.printMessage(kind, message, element, annotation, value);
            }
        };
    }

    @Override
    public Map<String, String> getOptions() {
        return delegate.getOptions();
    }

    @Override
    public Messager getMessager() {
        return messager;
    }

    @Override
    public Filer getFiler() {
        return delegate.getFiler();
    }

    @Override
    public Elements getElementUtils() {
        return delegate.getElementUtils();
    }

    @Override
    public Types getTypeUtils() {
        return delegate.getTypeUtils();
    }

    @Override
    public SourceVersion getSourceVersion() {
        return delegate.getSourceVersion();
    }

    @Override
    public Locale getLocale() {
        return delegate.getLocale();
    }

    @Override
    public boolean isPreviewEnabled() {
        return delegate.isPreviewEnabled();
    }
}
