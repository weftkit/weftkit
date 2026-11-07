package org.weftkit.wiring.bukkit.processor;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
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
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .contains("new org.weftkit.wiring.bukkit.scheduler.SchedulerModule(");
    }

    @Test
    void loadsCancellationBeforeDeclaredModulesAndTheirDependencies() {
        Compilation compilation = javac().withProcessors(new WiredProcessor())
                .compile(
                        JavaFileObjects.forSourceLines(
                                "test.Reg",
                                "package test;",
                                "import org.weftkit.wiring.Registry;",
                                "@Registry(modules = WorkerModule.class) public final class Reg {}"),
                        JavaFileObjects.forSourceLines(
                                "test.WorkerModule",
                                "package test;",
                                "import org.weftkit.wiring.WeftModule;",
                                "@WeftModule(id = \"test:worker\") public final class WorkerModule {",
                                "public WorkerModule(Worker worker) {} }"),
                        JavaFileObjects.forSourceLines(
                                "test.Worker",
                                "package test;",
                                "import org.weftkit.wiring.Wired;",
                                "import org.weftkit.wiring.Singleton;",
                                "import org.weftkit.wiring.bukkit.scheduler.Scheduler;",
                                "@Wired @Singleton public final class Worker { public Worker(Scheduler scheduler) {} }"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .containsMatch("LOAD_ORDER = List.of\\(\\s*org.weftkit.wiring.folia.SchedulerDetection.class,"
                        + "\\s*org.weftkit.wiring.bukkit.scheduler.SchedulerModule.class,"
                        + "\\s*test.Worker.class,\\s*test.WorkerModule.class");
    }

    @Test
    void disablingTheFirstModuleDoesNotReintroduceIt() {
        Compilation compilation =
                compile("@Registry(disable = BukkitModules.SCHEDULER) public final class Reg { public Reg() {} }");
        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .doesNotContain("org.weftkit.wiring.bukkit.scheduler.SchedulerModule");
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .contains("org.weftkit.wiring.bukkit.metrics.MetricsModule.class");
    }

    @Test
    void aReplacementRetainsTheFirstModulesLoadOrder() {
        Compilation compilation = compileSchedulerReplacement(false);
        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .containsMatch("LOAD_ORDER = List.of\\(\\s*test.MyScheduler.class,\\s*test.WorkerModule.class");
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .doesNotContain("org.weftkit.wiring.bukkit.scheduler.SchedulerModule");
    }

    @Test
    void disablingTheFirstModuleAlsoDropsItsReplacement() {
        Compilation compilation = compileSchedulerReplacement(true);
        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .doesNotContain("MyScheduler");
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .doesNotContain("org.weftkit.wiring.bukkit.scheduler.SchedulerModule");
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .containsMatch("LOAD_ORDER = List.of\\(\\s*test.WorkerModule.class");
    }

    private static Compilation compileSchedulerReplacement(boolean disabled) {
        return javac().withProcessors(new WiredProcessor())
                .compile(
                        JavaFileObjects.forSourceLines(
                                "test.Reg",
                                "package test;",
                                "import org.weftkit.wiring.Registry;",
                                "import org.weftkit.wiring.bukkit.BukkitModules;",
                                "@Registry(modules = {WorkerModule.class, MyScheduler.class}"
                                        + (disabled ? ", disable = BukkitModules.SCHEDULER" : "")
                                        + ") public final class Reg {}"),
                        JavaFileObjects.forSourceLines(
                                "test.WorkerModule",
                                "package test;",
                                "import org.weftkit.wiring.WeftModule;",
                                "@WeftModule(id = \"test:worker\") public final class WorkerModule {",
                                "public WorkerModule() {} }"),
                        JavaFileObjects.forSourceLines(
                                "test.MyScheduler",
                                "package test;",
                                "import org.weftkit.wiring.WeftModule;",
                                "import org.weftkit.wiring.bukkit.BukkitModules;",
                                "@WeftModule(id = BukkitModules.SCHEDULER) public final class MyScheduler {",
                                "public MyScheduler() {} }"));
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
    void declaresTheSchedulerAsAnAmbientType() {
        Compilation compilation = javac().withProcessors(new WiredProcessor())
                .compile(
                        JavaFileObjects.forSourceLines(
                                "test.Reg",
                                "package test;",
                                "import org.weftkit.wiring.Registry;",
                                "@Registry public final class Reg { public Reg() {} }"),
                        JavaFileObjects.forSourceLines(
                                "test.Ticker",
                                "package test;",
                                "import org.weftkit.wiring.Wired;",
                                "import org.weftkit.wiring.Singleton;",
                                "import org.weftkit.wiring.bukkit.scheduler.Scheduler;",
                                "@Wired @Singleton public final class Ticker { public Ticker(Scheduler scheduler) {} }"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .contains("org.weftkit.wiring.bukkit.scheduler.Scheduler.class");
    }

    @Test
    void weavesTheFoliaModuleWhenWeftkitFoliaIsOnTheClasspath() {
        Compilation compilation = compileTicker(javac());
        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .contains("org.weftkit.wiring.folia.FoliaModule.class");
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .contains("org.weftkit.wiring.folia.SchedulerDetection");
    }

    @Test
    void skipsTheFoliaModuleWithoutWeftkitFolia() {
        List<File> classpath = new ArrayList<>();
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator))
            if (!entry.contains("weftkit-folia")) classpath.add(new File(entry));
        Compilation compilation = compileTicker(javac().withClasspath(classpath));
        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .doesNotContain("FoliaModule");
    }

    private static Compilation compileTicker(Compiler compiler) {
        return compiler.withProcessors(new WiredProcessor())
                .compile(
                        JavaFileObjects.forSourceLines(
                                "test.Reg",
                                "package test;",
                                "import org.weftkit.wiring.Registry;",
                                "@Registry public final class Reg { public Reg() {} }"),
                        JavaFileObjects.forSourceLines(
                                "test.Ticker",
                                "package test;",
                                "import org.weftkit.wiring.Wired;",
                                "import org.weftkit.wiring.Singleton;",
                                "import org.weftkit.wiring.bukkit.scheduler.Scheduler;",
                                "@Wired @Singleton public final class Ticker { public Ticker(Scheduler scheduler) {} }"));
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
