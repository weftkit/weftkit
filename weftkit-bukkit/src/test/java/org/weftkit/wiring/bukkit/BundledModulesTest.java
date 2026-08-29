package org.weftkit.wiring.bukkit;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;
import org.weftkit.wiring.processor.WiredProcessor;

class BundledModulesTest {

    @Test
    void weavesTheBundledModulesIntoEveryGeneratedRegistry() {
        Compilation compilation = compile("@Registry public final class Reg { public Reg() {} }");
        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .contains("org.weftkit.wiring.bukkit.listeners.ListenerModule.class");
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .contains("org.weftkit.wiring.bukkit.commands.CommandModule.class");
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .contains("new org.weftkit.wiring.bukkit.metrics.MetricsModule(");
    }

    @Test
    void backsTheMetricsModuleWithGeneratedDefaultSettings() {
        Compilation compilation = compile("@Registry public final class Reg { public Reg() {} }");
        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("test.WeftMetricsSettingsDefaults")
                .contentsAsUtf8String()
                .contains("implements MetricsSettings");
    }

    @Test
    void bindsAPackagePrivateSettingsImplementationOverTheDefaults() {
        Compilation compilation = javac().withProcessors(new WiredProcessor())
                .compile(
                        JavaFileObjects.forSourceLines(
                                "test.Reg",
                                "package test;",
                                "import org.weftkit.wiring.Registry;",
                                "@Registry public final class Reg { public Reg() {} }"),
                        JavaFileObjects.forSourceLines(
                                "test.settings.MySettings",
                                "package test.settings;",
                                "import org.weftkit.wiring.Wired;",
                                "import org.weftkit.wiring.bukkit.metrics.MetricsSettings;",
                                "@Wired final class MySettings implements MetricsSettings {",
                                "    MySettings() {}",
                                "    public boolean enabled() { return false; }",
                                "}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .doesNotContain("WeftMetricsSettingsDefaults");
        // The hidden implementation binds through its package fragment
        assertThat(compilation)
                .generatedSourceFile("test.settings.WeftWiring")
                .contentsAsUtf8String()
                .contains("MySettings");
    }

    @Test
    void disablingDropsTheModuleFromTheRegistry() {
        Compilation compilation =
                compile("@Registry(disable = BukkitModules.METRICS) public final class Reg { public Reg() {} }");
        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .doesNotContain("MetricsModule");
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .contains("org.weftkit.wiring.bukkit.listeners.ListenerModule.class");
    }

    @Test
    void unknownDisableIdFailsTheBuild() {
        Compilation compilation =
                compile("@Registry(disable = \"bukit:metrics\") public final class Reg { public Reg() {} }");
        assertThat(compilation).hadErrorContaining("unknown module id");
    }

    private static Compilation compile(String registry) {
        return javac().withProcessors(new WiredProcessor())
                .compile(
                        JavaFileObjects.forSourceLines(
                                "test.Reg",
                                "package test;",
                                "import org.weftkit.wiring.Registry;",
                                "import org.weftkit.wiring.bukkit.modules.BukkitModules;",
                                registry),
                        JavaFileObjects.forSourceLines(
                                "test.Svc",
                                "package test;",
                                "import org.weftkit.wiring.Wired;",
                                "import org.weftkit.wiring.Singleton;",
                                "@Wired @Singleton public final class Svc { public Svc(Reg reg) {} }"));
    }
}
