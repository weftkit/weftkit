package org.weftkit.wiring.processor;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.io.Writer;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import org.junit.jupiter.api.Test;

class ProcessingRoundsTest {

    @Test
    void rejectsALateGeneratedComponentInsteadOfSilentlyOmittingIt() {
        Compilation compilation = compile("@org.weftkit.wiring.Wired public class Late {}");
        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("weftkit cannot include test.Late");
        assertThat(compilation).hadErrorContaining("generated after the wiring registry");
    }

    @Test
    void rejectsALateGeneratedRegistry() {
        Compilation compilation = compile("@org.weftkit.wiring.Registry public class Late {}");
        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("Late-generated @Wired and @Registry types are unsupported");
    }

    @Test
    void unrelatedGeneratedSourcesRemainSupported() {
        assertThat(compile("public class Late {}")).succeeded();
    }

    private Compilation compile(String generated) {
        return javac().withProcessors(new Generator(generated), new WiredProcessor())
                .compile(JavaFileObjects.forSourceString("test.Reg", """
                        package test;
                        @org.weftkit.wiring.Registry
                        public class Reg {}
                        """));
    }

    private static final class Generator extends AbstractProcessor {
        private final String source;
        private boolean generated;

        Generator(String source) {
            this.source = source;
        }

        @Override
        public Set<String> getSupportedAnnotationTypes() {
            return Set.of("*");
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
            if (!generated && !round.processingOver()) {
                generated = true;
                try (Writer writer =
                        processingEnv.getFiler().createSourceFile("test.Late").openWriter()) {
                    writer.write("package test; " + source);
                } catch (IOException ex) {
                    throw new IllegalStateException(ex);
                }
            }
            return false;
        }
    }
}
