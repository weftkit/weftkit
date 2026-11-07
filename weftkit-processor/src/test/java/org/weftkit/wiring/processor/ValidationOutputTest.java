package org.weftkit.wiring.processor;

import static org.junit.jupiter.api.Assertions.*;

import com.google.testing.compile.JavaFileObjects;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weftkit.wiring.Registry;

class ValidationOutputTest {
    @TempDir
    Path temporary;

    @Test
    void elementErrorProducesNoRegistryOrGraph() throws Exception {
        invalid("@Wired public abstract static class Invalid {}", "@Wired requires a concrete class");
    }

    @Test
    void missingDependencyProducesNoRegistryOrGraph() throws Exception {
        invalid("""
                public static class Missing {}
                @Wired @Singleton public static class Invalid { public Invalid(Missing value) {} }
                """, "Dependency must be @Wired or a @Provides product");
    }

    @Test
    void cycleProducesNoRegistryOrGraph() throws Exception {
        invalid("""
                @Wired @Singleton public static class A { public A(B value) {} }
                @Wired @Singleton public static class B { public B(A value) {} }
                """, "Component dependency cycle");
    }

    @Test
    void treeDiagnosticProducesNoRegistryOrGraph() throws Exception {
        invalid("""
                @StaticHolder public static class Settings { public static int VALUE = 1; }
                @Wired @Singleton public static class Invalid implements Loader {
                    public boolean load() { return Settings.VALUE > 0; }
                }
                """, "Static holder is accessed during load without @Requires");
    }

    @Test
    void adapterDiagnosticProducesNoRegistryOrGraph() throws Exception {
        invalid("@Wired public static class RuleError {}", "Fixture adapter rejected this component");
    }

    @Test
    void warningsStillAllowGeneration() throws Exception {
        Result result = compile("""
                public static class Missing {}
                @Wired @Singleton public static class Valid {
                    public Valid(java.util.Optional<Missing> value) {}
                }
                """);
        assertTrue(result.success(), result.messages());
        assertTrue(result.messages().contains("can never be present"), result.messages());
        assertTrue(Files.exists(result.output().resolve("test/WeftWiring.java")));
        assertTrue(Files.exists(result.output().resolve("test/weftkit-graph.dot")));
    }

    private void invalid(String declarations, String expected) throws Exception {
        Result result = compile(declarations);
        assertFalse(result.success());
        assertTrue(result.messages().contains(expected), result.messages());
        try (var files = Files.walk(result.output())) {
            assertEquals(List.of(), files.filter(Files::isRegularFile).toList(), result.messages());
        }
        assertFalse(result.messages().contains("Failed to generate"), result.messages());
    }

    private Result compile(String declarations) throws Exception {
        Path output = Files.createDirectory(temporary.resolve("generated"));
        var compiler = ToolProvider.getSystemJavaCompiler();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var files = compiler.getStandardFileManager(diagnostics, Locale.ROOT, null)) {
            files.setLocationFromPaths(StandardLocation.SOURCE_OUTPUT, List.of(output));
            files.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(temporary));
            files.setLocationFromPaths(
                    StandardLocation.CLASS_PATH,
                    List.of(Path.of(Registry.class
                            .getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI())));
            JavaFileObject source = JavaFileObjects.forSourceString("test.Reg", """
                    package test;
                    import org.weftkit.wiring.*;
                    @Registry public class Reg {
                    """ + declarations + "\n}");
            var task = compiler.getTask(null, files, diagnostics, List.of("-proc:only"), null, List.of(source));
            task.setProcessors(List.of(new WiredProcessor()));
            boolean success = task.call();
            String messages = diagnostics.getDiagnostics().stream()
                    .map(diagnostic -> diagnostic.getKind() + ": " + diagnostic.getMessage(Locale.ROOT))
                    .collect(java.util.stream.Collectors.joining("\n"));
            return new Result(success, messages, output);
        }
    }

    private record Result(boolean success, String messages, Path output) {}
}
