package org.weftkit.wiring.bukkit.processor;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.StandardLocation;
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

    @Test
    void rejectsAHandlerAnnotationOfADisabledModule() {
        Compilation compilation = javac().withProcessors(new WiredProcessor())
                .compile(
                        JavaFileObjects.forSourceLines(
                                "test.Reg",
                                "package test;",
                                "import org.weftkit.wiring.Registry;",
                                "import org.weftkit.wiring.bukkit.BukkitModules;",
                                "@Registry(disable = BukkitModules.COMMANDS) public final class Reg { public Reg() {} }"),
                        JavaFileObjects.forSourceLines(
                                "test.Cmd",
                                "package test;",
                                "import org.weftkit.wiring.Wired;",
                                "import org.weftkit.wiring.Singleton;",
                                "import org.weftkit.wiring.bukkit.commands.CommandHandler;",
                                "@Wired @Singleton @CommandHandler(\"home\") public final class Cmd",
                                "    implements org.bukkit.command.CommandExecutor {",
                                "  public Cmd() {}",
                                "  public boolean onCommand(org.bukkit.command.CommandSender s, org.bukkit.command.Command c,",
                                "      String l, String[] a) { return true; }",
                                "}"));
        assertThat(compilation)
                .hadErrorContaining(
                        "Component test.Cmd carries @CommandHandler, handled by module bukkit:commands, which @Registry disables");
    }

    @Test
    void handsAHandlerAnnotationToTheModuleTakingOverItsId() {
        Compilation compilation = javac().withProcessors(new WiredProcessor())
                .compile(
                        JavaFileObjects.forSourceLines(
                                "test.Reg",
                                "package test;",
                                "import org.weftkit.wiring.Registry;",
                                "@Registry(modules = MyCommands.class) public final class Reg { public Reg() {} }"),
                        JavaFileObjects.forSourceLines(
                                "test.MyCommands",
                                "package test;",
                                "import org.weftkit.wiring.WeftModule;",
                                "import org.weftkit.wiring.bukkit.BukkitModules;",
                                "@WeftModule(id = BukkitModules.COMMANDS) public final class MyCommands {",
                                "  public MyCommands() {}",
                                "}"),
                        JavaFileObjects.forSourceLines(
                                "test.Cmd",
                                "package test;",
                                "import org.weftkit.wiring.Wired;",
                                "import org.weftkit.wiring.Singleton;",
                                "import org.weftkit.wiring.bukkit.commands.CommandHandler;",
                                "@Wired @Singleton @CommandHandler(\"home\") public final class Cmd",
                                "    implements org.bukkit.command.CommandExecutor {",
                                "  public Cmd() {}",
                                "  public boolean onCommand(org.bukkit.command.CommandSender s, org.bukkit.command.Command c,",
                                "      String l, String[] a) { return true; }",
                                "}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .contains("org.weftkit.wiring.bukkit.commands.CommandHandler.class, \"test.MyCommands\")");
        assertThat(compilation)
                .generatedFile(StandardLocation.SOURCE_OUTPUT, "test", "weftkit-graph.dot")
                .contentsAsUtf8String()
                .doesNotContain("inert");
    }

    private static Compilation compile(String registry) {
        return javac().withProcessors(new WiredProcessor())
                .compile(
                        JavaFileObjects.forSourceLines(
                                "test.Reg",
                                "package test;",
                                "import org.weftkit.wiring.Registry;",
                                "import org.weftkit.wiring.bukkit.BukkitModules;",
                                registry),
                        JavaFileObjects.forSourceLines(
                                "test.Svc",
                                "package test;",
                                "import org.weftkit.wiring.Wired;",
                                "import org.weftkit.wiring.Singleton;",
                                "@Wired @Singleton public final class Svc { public Svc(Reg reg) {} }"));
    }
}
