package org.weftkit.wiring.paper.processor;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;
import org.weftkit.wiring.processor.WiredProcessor;

class BundledModulesTest {

    @Test
    void weavesThePaperAndBukkitModulesIntoEveryGeneratedRegistry() {
        Compilation compilation = compile("@Registry public final class Reg { public Reg() {} }");
        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .contains("org.weftkit.wiring.paper.commands.PaperCommandModule.class");
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .contains("org.weftkit.wiring.bukkit.listeners.ListenerModule.class");
    }

    @Test
    void disablingDropsTheModuleFromTheRegistry() {
        Compilation compilation =
                compile("@Registry(disable = PaperModules.COMMANDS) public final class Reg { public Reg() {} }");
        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .doesNotContain("PaperCommandModule");
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .contains("org.weftkit.wiring.bukkit.commands.CommandModule.class");
    }

    private static Compilation compile(String registry) {
        return javac().withProcessors(new WiredProcessor())
                .compile(JavaFileObjects.forSourceLines(
                        "test.Reg",
                        "package test;",
                        "import org.weftkit.wiring.Registry;",
                        "import org.weftkit.wiring.paper.PaperModules;",
                        registry));
    }
}
