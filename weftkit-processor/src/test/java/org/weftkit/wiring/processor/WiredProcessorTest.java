package org.weftkit.wiring.processor;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WiredProcessorTest {

    private static Compilation compile(JavaFileObject... sources) {
        return javac().withProcessors(new WiredProcessor()).compile(sources);
    }

    private static JavaFileObject registry() {
        return JavaFileObjects.forSourceLines(
                "test.Reg",
                "package test;",
                "import org.weftkit.wiring.Registry;",
                "@Registry public final class Reg { public Reg() {} }");
    }

    @Test
    void acceptsAValidGraphAndGeneratesTheRegistry() {
        Compilation compilation = compile(
                registry(),
                JavaFileObjects.forSourceLines(
                        "test.Svc",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "@Wired @Singleton public final class Svc { public Svc(Reg reg) {} }"));
        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("test.WeftWiring");
        assertThat(compilation).generatedFile(StandardLocation.SOURCE_OUTPUT, "test", "weftkit-graph.dot");
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .doesNotContain("@SuppressWarnings");
    }

    private static JavaFileObject moduleRegistry() {
        return JavaFileObjects.forSourceLines(
                "test.Reg",
                "package test;",
                "import org.weftkit.wiring.Registry;",
                "@Registry(modules = Mod.class) public final class Reg { public Reg() {} }");
    }

    private static JavaFileObject component() {
        return JavaFileObjects.forSourceLines(
                "test.Svc",
                "package test;",
                "import org.weftkit.wiring.Wired;",
                "import org.weftkit.wiring.Singleton;",
                "@Wired @Singleton public final class Svc { public Svc(Reg reg) {} }");
    }

    private static JavaFileObject module() {
        return JavaFileObjects.forSourceLines(
                "test.Mod",
                "package test;",
                "import org.weftkit.wiring.ModuleLifecycle;",
                "import org.weftkit.wiring.WeftModule;",
                "@WeftModule(id = \"test:mod\")",
                "public final class Mod implements ModuleLifecycle {",
                "    public Mod(Svc svc) {}",
                "}");
    }

    // test.Contributed is contributed by the test FixtureContributor service
    private static JavaFileObject contributed() {
        return JavaFileObjects.forSourceLines(
                "test.Contributed",
                "package test;",
                "import org.weftkit.wiring.WeftModule;",
                "@WeftModule(id = \"test:contributed\")",
                "public final class Contributed {",
                "    public Contributed() {}",
                "}");
    }

    @Test
    void weavesContributedModulesWhenTheirClassIsOnTheClasspath() {
        Compilation compilation = compile(registry(), component(), contributed());
        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .contains("test.Contributed.class");
    }

    @Test
    void warnsOnContributedModulesSharingAnId() {
        Compilation compilation = compile(
                registry(),
                component(),
                contributed(),
                JavaFileObjects.forSourceLines(
                        "test.ContributedClash",
                        "package test;",
                        "import org.weftkit.wiring.WeftModule;",
                        "@WeftModule(id = \"test:contributed\")",
                        "public final class ContributedClash {",
                        "    public ContributedClash() {}",
                        "}"));
        assertThat(compilation).succeeded();
        assertThat(compilation).hadWarningContaining("share the id test:contributed");
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .doesNotContain("ContributedClash");
    }

    @Test
    void skipsAContributedModuleAbsentFromTheCompileClasspath() {
        Compilation compilation = compile(registry(), component());
        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .doesNotContain("Contributed");
    }

    @Test
    void recordsHandledAnnotationsWithEveryValueKind() {
        Compilation compilation = compile(
                moduleRegistry(),
                component(),
                module(),
                JavaFileObjects.forSourceLines(
                        "test.Tag",
                        "package test;",
                        "import java.lang.annotation.*;",
                        "import org.weftkit.wiring.HandledBy;",
                        "@HandledBy(Mod.class) @Retention(RetentionPolicy.CLASS)",
                        "public @interface Tag {",
                        "    String value();",
                        "    int count() default 3;",
                        "    long big() default Long.MIN_VALUE;",
                        "    char letter() default 'x';",
                        "    double ratio() default Double.NaN;",
                        "    float scale() default 1.5f;",
                        "    String[] names() default {\"a\", \"b\"};",
                        "    Class<? extends Number> type() default Integer.class;",
                        "    Class<?>[] types() default {};",
                        "    ElementType kind() default ElementType.TYPE;",
                        "    Deprecated nested() default @Deprecated(since = \"1\");",
                        "}"),
                JavaFileObjects.forSourceLines(
                        "test.Tagged",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "@Wired @Tag(value = \"t\", names = \"only\") public final class Tagged { public Tagged() {} }"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .contains("new String[] {\"only\"}");
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .contains("Map.entry(test.Tag.class, \"test.Mod\")");
        assertThat(compilation)
                .generatedFile(StandardLocation.SOURCE_OUTPUT, "test", "weftkit-graph.dot")
                .contentsAsUtf8String()
                .contains("\"test.Mod\" -> \"test.Tagged\" [style=dotted");
    }

    @Test
    void marksAComponentInertWhenItsHandlingModuleIsMissing() {
        Compilation compilation = compile(
                registry(),
                component(),
                JavaFileObjects.forSourceLines(
                        "test.Orphan",
                        "package test;",
                        "import org.weftkit.wiring.HandledBy;",
                        "@HandledBy(Absent.class) public @interface Orphan {}"),
                JavaFileObjects.forSourceLines(
                        "test.Absent",
                        "package test;",
                        "import org.weftkit.wiring.WeftModule;",
                        "@WeftModule(id = \"test:absent\") public final class Absent { public Absent() {} }"),
                JavaFileObjects.forSourceLines(
                        "test.Orphaned",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "@Wired @Orphan public final class Orphaned { public Orphaned() {} }"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedFile(StandardLocation.SOURCE_OUTPUT, "test", "weftkit-graph.dot")
                .contentsAsUtf8String()
                .contains("inert: no Absent");
        assertThat(compilation)
                .generatedFile(StandardLocation.SOURCE_OUTPUT, "test", "weftkit-graph.dot")
                .contentsAsUtf8String()
                .doesNotContain("\"test.Absent\" -> \"test.Orphaned\"");
    }

    @Test
    void rendersHandledAnnotationsWithoutLintWarnings() {
        Compilation compilation = javac().withProcessors(new WiredProcessor())
                .withOptions("-Xlint:rawtypes,unchecked,cast", "-Werror")
                .compile(
                        moduleRegistry(),
                        component(),
                        module(),
                        JavaFileObjects.forSourceLines(
                                "test.Tag",
                                "package test;",
                                "import org.weftkit.wiring.HandledBy;",
                                "@HandledBy(Mod.class) public @interface Tag {",
                                "    Class<?>[] types() default {String.class};",
                                "    Class<? extends Number>[] numbers() default {Integer.class};",
                                "    Class<? extends Number> number() default Long.class;",
                                "}"),
                        JavaFileObjects.forSourceLines(
                                "test.Tagged",
                                "package test;",
                                "import org.weftkit.wiring.Wired;",
                                "@Wired @Tag public final class Tagged { public Tagged() {} }"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .contains("new Class<?>[] {String.class}");
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .contains("public boolean equals(Object other)");
    }

    @Test
    void recordsEveryRepeatedHandledAnnotation() {
        Compilation compilation = compile(
                moduleRegistry(),
                component(),
                module(),
                JavaFileObjects.forSourceLines(
                        "test.Tag",
                        "package test;",
                        "import java.lang.annotation.Repeatable;",
                        "import org.weftkit.wiring.HandledBy;",
                        "@HandledBy(Mod.class) @Repeatable(Tags.class) public @interface Tag { String value(); }"),
                JavaFileObjects.forSourceLines(
                        "test.Tags", "package test;", "public @interface Tags { Tag[] value(); }"),
                JavaFileObjects.forSourceLines(
                        "test.Tagged",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "@Wired @Tag(\"first\") @Tag(\"second\") public final class Tagged { public Tagged() {} }"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .contains("return \"first\"");
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .contains("return \"second\"");
    }

    @Test
    void recordsAHandledRepeatableContainer() {
        Compilation compilation = compile(
                moduleRegistry(),
                component(),
                module(),
                JavaFileObjects.forSourceLines(
                        "test.Route",
                        "package test;",
                        "import java.lang.annotation.Repeatable;",
                        "@Repeatable(Routes.class) public @interface Route { String value(); }"),
                JavaFileObjects.forSourceLines(
                        "test.Routes",
                        "package test;",
                        "import org.weftkit.wiring.HandledBy;",
                        "@HandledBy(Mod.class) public @interface Routes { Route[] value(); }"),
                JavaFileObjects.forSourceLines(
                        "test.Routed",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "@Wired @Route(\"first\") @Route(\"second\") public final class Routed {"
                                + " public Routed() {} }"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .contains("Map.entry(test.Routes.class, \"test.Mod\")");
    }

    @Test
    void rejectsAHandlerThatIsNotAModule() {
        Compilation compilation = compile(
                registry(),
                component(),
                JavaFileObjects.forSourceLines(
                        "test.Orphan",
                        "package test;",
                        "import org.weftkit.wiring.HandledBy;",
                        "@HandledBy(Svc.class) public @interface Orphan {}"),
                JavaFileObjects.forSourceLines(
                        "test.Orphaned",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "@Wired @Orphan public final class Orphaned { public Orphaned() {} }"));
        assertThat(compilation)
                .hadErrorContaining(
                        "Component test.Orphaned carries @Orphan, handled by test.Svc, which is not a @WeftModule");
    }

    @Test
    void rejectsAHandledAnnotationTheRegistryCannotReach() {
        Compilation compilation = compile(
                moduleRegistry(),
                component(),
                module(),
                JavaFileObjects.forSourceLines(
                        "test.other.Hidden",
                        "package test.other;",
                        "import org.weftkit.wiring.HandledBy;",
                        "@HandledBy(test.Mod.class) @interface Hidden {}"),
                JavaFileObjects.forSourceLines(
                        "test.other.Carrier",
                        "package test.other;",
                        "import org.weftkit.wiring.Wired;",
                        "@Wired @Hidden public final class Carrier { public Carrier() {} }"));
        assertThat(compilation)
                .hadErrorContaining(
                        "Component test.other.Carrier carries @Hidden naming test.other.Hidden, which must be public");
    }

    @Test
    void rejectsAHandledAnnotationMemberTypeTheRegistryCannotReach() {
        Compilation compilation = compile(
                moduleRegistry(),
                component(),
                module(),
                JavaFileObjects.forSourceLines("test.other.Level", "package test.other;", "enum Level { LOW }"),
                JavaFileObjects.forSourceLines(
                        "test.other.Ranked",
                        "package test.other;",
                        "import org.weftkit.wiring.HandledBy;",
                        "@HandledBy(test.Mod.class) public @interface Ranked {",
                        "  Level[] levels() default {};",
                        "  Class<? extends Level> kind() default Level.class;",
                        "}"),
                JavaFileObjects.forSourceLines(
                        "test.other.Carrier",
                        "package test.other;",
                        "import org.weftkit.wiring.Wired;",
                        "@Wired @Ranked public final class Carrier { public Carrier() {} }"));
        assertThat(compilation)
                .hadErrorContaining(
                        "Component test.other.Carrier carries @Ranked naming test.other.Level, which must be public");
    }

    private static List<File> classpathWith(Path classes, Compilation library) throws IOException {
        for (JavaFileObject file : library.generatedFiles()) {
            Path target = classes.resolve(file.toUri().getPath().replaceFirst("^/CLASS_OUTPUT/", ""));
            Files.createDirectories(target.getParent());
            try (InputStream in = file.openInputStream()) {
                Files.copy(in, target);
            }
        }
        List<File> classpath = new ArrayList<>(List.of(classes.toFile()));
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator))
            classpath.add(new File(entry));
        return classpath;
    }

    @Test
    void rejectsAModuleComponentWhoseHandlerIsDisabled(@TempDir Path classes) throws IOException {
        Compilation library = javac().withOptions("-proc:none")
                .compile(
                        JavaFileObjects.forSourceLines(
                                "lib.Off",
                                "package lib;",
                                "import org.weftkit.wiring.WeftModule;",
                                "@WeftModule(id = \"lib:off\") public final class Off { public Off() {} }"),
                        JavaFileObjects.forSourceLines(
                                "lib.Gated",
                                "package lib;",
                                "import org.weftkit.wiring.HandledBy;",
                                "@HandledBy(Off.class) public @interface Gated {}"),
                        JavaFileObjects.forSourceLines(
                                "lib.Shipped",
                                "package lib;",
                                "import org.weftkit.wiring.Wired;",
                                "@Wired @Gated public final class Shipped { public Shipped() {} }"),
                        JavaFileObjects.forSourceLines(
                                "lib.On",
                                "package lib;",
                                "import org.weftkit.wiring.WeftModule;",
                                "@WeftModule(id = \"lib:on\", components = Shipped.class)",
                                "public final class On { public On() {} }"));
        assertThat(library).succeeded();
        List<File> classpath = classpathWith(classes, library);
        Compilation compilation = javac().withProcessors(new WiredProcessor())
                .withClasspath(classpath)
                .compile(JavaFileObjects.forSourceLines(
                        "test.Reg",
                        "package test;",
                        "import org.weftkit.wiring.Registry;",
                        "@Registry(modules = {lib.On.class, lib.Off.class}, disable = \"lib:off\")",
                        "public final class Reg { public Reg() {} }"));
        assertThat(compilation)
                .hadErrorContaining(
                        "Component lib.Shipped carries @Gated, handled by module lib:off, which @Registry disables");
    }

    @Test
    void weavesDeclaredModulesAsInjectedSingletons() {
        Compilation compilation = compile(moduleRegistry(), component(), module());
        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .contains("new test.Mod(");
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .contains("test.Mod.class,");
    }

    @Test
    void ordersModuleActivationByInjection() {
        Compilation compilation = compile(
                JavaFileObjects.forSourceLines(
                        "test.Reg",
                        "package test;",
                        "import org.weftkit.wiring.Registry;",
                        "@Registry(modules = {Second.class, First.class})",
                        "public final class Reg { public Reg() {} }"),
                JavaFileObjects.forSourceLines(
                        "test.First",
                        "package test;",
                        "import org.weftkit.wiring.WeftModule;",
                        "@WeftModule(id = \"test:first\")",
                        "public final class First { public First() {} }"),
                JavaFileObjects.forSourceLines(
                        "test.Second",
                        "package test;",
                        "import org.weftkit.wiring.WeftModule;",
                        "@WeftModule(id = \"test:second\")",
                        "public final class Second { public Second(First first) {} }"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .containsMatch("MODULES = List.of\\(\\s*test.First.class,\\s*test.Second.class\\)");
    }

    @Test
    void rejectsAModuleWithoutTheAnnotation() {
        Compilation compilation = compile(
                moduleRegistry(),
                component(),
                JavaFileObjects.forSourceLines(
                        "test.Mod", "package test;", "public final class Mod { public Mod() {} }"));
        assertThat(compilation).hadErrorContaining("is not annotated with @WeftModule");
    }

    @Test
    void weavesModuleComponentsFromTheClasspathIntoTheGraph() {
        Compilation compilation = compile(fixtureRegistry(""), component());
        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .contains("new org.weftkit.wiring.processor.fixture.FixtureComponent()");
    }

    @Test
    void disablingAModuleDropsItAndItsComponents() {
        Compilation compilation = compile(fixtureRegistry(", disable = \"fixture:mod\""), component());
        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .doesNotContain("Fixture");
    }

    private static JavaFileObject fixtureRegistry(String disable) {
        return JavaFileObjects.forSourceLines(
                "test.Reg",
                "package test;",
                "import org.weftkit.wiring.Registry;",
                "import org.weftkit.wiring.processor.fixture.FixtureModule;",
                "@Registry(modules = FixtureModule.class" + disable + ")",
                "public final class Reg { public Reg() {} }");
    }

    @Test
    void generatesDefaultsForUnwiredModuleSettings() {
        Compilation compilation = compile(moduleRegistry(), component(), settings(), settingsModule());
        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("test.WeftSettingsDefaults")
                .contentsAsUtf8String()
                .contains("implements Settings");
    }

    @Test
    void wiredSettingsImplementationOverridesTheDefaults() {
        Compilation compilation = compile(
                moduleRegistry(),
                component(),
                settings(),
                settingsModule(),
                JavaFileObjects.forSourceLines(
                        "test.Impl",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "@Wired public final class Impl implements Settings { public Impl() {} }"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .doesNotContain("WeftSettingsDefaults");
    }

    @Test
    void rejectsSettingsWithAbstractMethodsWhenNoImplementationIsWired() {
        Compilation compilation = compile(
                moduleRegistry(),
                component(),
                JavaFileObjects.forSourceLines(
                        "test.Settings",
                        "package test;",
                        "import org.weftkit.wiring.ModuleSettings;",
                        "@ModuleSettings public interface Settings { int limit(); }"),
                settingsModule());
        assertThat(compilation).hadErrorContaining("non-default methods");
    }

    private static JavaFileObject settings() {
        return JavaFileObjects.forSourceLines(
                "test.Settings",
                "package test;",
                "import org.weftkit.wiring.ModuleSettings;",
                "@ModuleSettings public interface Settings { default int limit() { return 3; } }");
    }

    private static JavaFileObject settingsModule() {
        return JavaFileObjects.forSourceLines(
                "test.Mod",
                "package test;",
                "import org.weftkit.wiring.WeftModule;",
                "@WeftModule(id = \"test:mod\")",
                "public final class Mod { public Mod(Settings settings) {} }");
    }

    @Test
    void rejectsWeftLoaderDependencies() {
        Compilation compilation = compile(
                registry(),
                JavaFileObjects.forSourceLines(
                        "test.Grabby",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.runtime.WeftLoader;",
                        "@Wired public final class Grabby { public Grabby(WeftLoader loader) {} }"));
        assertThat(compilation).hadErrorContaining("WeftLoader is not injectable");
    }

    @Test
    void rejectsDependenciesOnFeatureFlaggedComponents() {
        Compilation compilation = compile(
                registry(),
                JavaFileObjects.forSourceLines(
                        "test.Gated",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "import org.weftkit.wiring.FeatureFlag;",
                        "@Wired @Singleton @FeatureFlag(\"regions\")",
                        "public final class Gated { public Gated() {} }"),
                JavaFileObjects.forSourceLines(
                        "test.Uses",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "@Wired public final class Uses { public Uses(Gated gated) {} }"));
        assertThat(compilation).hadErrorContaining("may never be constructed");
    }

    @Test
    void acceptsOptionalDependencyOnFeatureFlaggedComponent() {
        Compilation compilation = compile(
                registry(),
                JavaFileObjects.forSourceLines(
                        "test.Gated",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "import org.weftkit.wiring.FeatureFlag;",
                        "@Wired @Singleton @FeatureFlag(\"regions\")",
                        "public final class Gated { public Gated() {} }"),
                JavaFileObjects.forSourceLines(
                        "test.Uses",
                        "package test;",
                        "import java.util.Optional;",
                        "import org.weftkit.wiring.Wired;",
                        "@Wired public final class Uses { public Uses(Optional<Gated> gated) {} }"),
                flagsImplementation("ZFlags"));
        assertThat(compilation).succeeded();
    }

    @Test
    void recordsFeatureFlagsInTheRegistry() {
        Compilation compilation = compile(
                registry(),
                JavaFileObjects.forSourceLines(
                        "test.Gated",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "import org.weftkit.wiring.FeatureFlag;",
                        "@Wired @Singleton @FeatureFlag(value = \"regions\", enabledByDefault = false)",
                        "public final class Gated { public Gated() {} }"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .containsMatch("Map.entry\\(test.Gated.class, new Flag\\(\"regions\", false\\)\\)");
    }

    @Test
    void rejectsSeveralFeatureFlagsImplementations() {
        Compilation compilation = compile(registry(), flagsImplementation("FlagsA"), flagsImplementation("FlagsB"));
        assertThat(compilation).hadErrorContaining("Several FeatureFlags implementations");
    }

    @Test
    void ordersTheFeatureFlagsImplementationBeforeFlaggedComponents() {
        Compilation compilation = compile(
                registry(),
                JavaFileObjects.forSourceLines(
                        "test.Gated",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "import org.weftkit.wiring.FeatureFlag;",
                        "@Wired @Singleton @FeatureFlag(\"regions\")",
                        "public final class Gated { public Gated() {} }"),
                flagsImplementation("ZFlags"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .containsMatch("LOAD_ORDER = List.of\\(\\s*test.ZFlags.class,\\s*test.Gated.class\\)");
    }

    private static JavaFileObject flagsImplementation(String name) {
        return JavaFileObjects.forSourceLines(
                "test." + name,
                "package test;",
                "import org.weftkit.wiring.Wired;",
                "import org.weftkit.wiring.Singleton;",
                "import org.weftkit.wiring.FeatureFlags;",
                "@Wired @Singleton public final class " + name + " implements FeatureFlags {",
                "    public " + name + "() {}",
                "    public boolean isEnabled(String key) { return true; }",
                "}");
    }

    @Test
    void graphBadgesFeatureFlaggedComponents() {
        Compilation compilation = compile(
                registry(),
                JavaFileObjects.forSourceLines(
                        "test.Gated",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "import org.weftkit.wiring.FeatureFlag;",
                        "@Wired @Singleton @FeatureFlag(\"regions\")",
                        "public final class Gated { public Gated(Reg reg) {} }"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedFile(StandardLocation.SOURCE_OUTPUT, "test", "weftkit-graph.dot")
                .contentsAsUtf8String()
                .contains("feature: regions");
    }

    @Test
    void suppressesUncheckedOnlyForParameterizedCasts() {
        Compilation compilation = compile(
                registry(),
                JavaFileObjects.forSourceLines(
                        "test.Prov",
                        "package test;",
                        "import java.util.List;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "import org.weftkit.wiring.Provides;",
                        "@Wired @Singleton public final class Prov {",
                        "  public Prov() {}",
                        "  @Provides public List<String> names() { return List.of(); }",
                        "}"),
                JavaFileObjects.forSourceLines(
                        "test.Uses",
                        "package test;",
                        "import java.util.List;",
                        "import org.weftkit.wiring.Wired;",
                        "@Wired public final class Uses { public Uses(List<String> names) {} }"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .contains("@SuppressWarnings(\"unchecked\")");
    }

    @Test
    void rejectsLoaderThatIsNotSingleton() {
        Compilation compilation = compile(
                registry(),
                JavaFileObjects.forSourceLines(
                        "test.Svc",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Loader;",
                        "@Wired public final class Svc implements Loader {",
                        "  public Svc() {}",
                        "  public boolean load() { return true; }",
                        "}"));
        assertThat(compilation).hadErrorContaining("Loader implementations must be @Singleton");
    }

    @Test
    void rejectsSingletonWithoutWired() {
        Compilation compilation = compile(
                registry(),
                JavaFileObjects.forSourceLines(
                        "test.Svc",
                        "package test;",
                        "import org.weftkit.wiring.Singleton;",
                        "@Singleton public final class Svc { public Svc() {} }"));
        assertThat(compilation).hadErrorContaining("@Singleton classes must be @Wired");
    }

    @Test
    void rejectsProvidesOnNonSingleton() {
        Compilation compilation = compile(
                registry(),
                JavaFileObjects.forSourceLines(
                        "test.Prov",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Provides;",
                        "@Wired public final class Prov {",
                        "  public Prov() {}",
                        "  @Provides public String thing() { return \"\"; }",
                        "}"));
        assertThat(compilation).hadErrorContaining("@Provides getters must live on a @Wired singleton");
    }

    @Test
    void rejectsDuplicateRegistry() {
        Compilation compilation = compile(
                registry(),
                JavaFileObjects.forSourceLines(
                        "test.Other",
                        "package test;",
                        "import org.weftkit.wiring.Registry;",
                        "@Registry public final class Other { public Other() {} }"));
        assertThat(compilation).hadErrorContaining("@Registry is already declared on");
    }

    @Test
    void rejectsMissingRegistry() {
        Compilation compilation = compile(JavaFileObjects.forSourceLines(
                "test.Svc",
                "package test;",
                "import org.weftkit.wiring.Wired;",
                "import org.weftkit.wiring.Singleton;",
                "@Wired @Singleton public final class Svc { public Svc() {} }"));
        assertThat(compilation).hadErrorContaining("No @Registry class to generate");
    }

    @Test
    void rejectsDependencyCycle() {
        Compilation compilation = compile(
                registry(),
                JavaFileObjects.forSourceLines(
                        "test.A",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "@Wired @Singleton public final class A { public A(B b) {} }"),
                JavaFileObjects.forSourceLines(
                        "test.B",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "@Wired @Singleton public final class B { public B(A a) {} }"));
        assertThat(compilation).hadErrorContaining("Component dependency cycle");
    }

    @Test
    void reportsOnlyTheCycleSegment() {
        Compilation compilation = compile(
                registry(),
                JavaFileObjects.forSourceLines(
                        "test.A",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "@Wired @Singleton public final class A { public A(B b) {} }"),
                JavaFileObjects.forSourceLines(
                        "test.B",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "@Wired @Singleton public final class B { public B(C c) {} }"),
                JavaFileObjects.forSourceLines(
                        "test.C",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "@Wired @Singleton public final class C { public C(B b) {} }"));
        // A merely leads into the cycle, so the reported cycle starts at its first actual member
        assertThat(compilation).hadErrorContaining("Component dependency cycle: test.B -> test.C -> test.B");
    }

    @Test
    void rejectsUnresolvableDependency() {
        Compilation compilation = compile(
                registry(),
                JavaFileObjects.forSourceLines(
                        "test.Plain", "package test;", "public final class Plain { public Plain() {} }"),
                JavaFileObjects.forSourceLines(
                        "test.Needs",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "@Wired @Singleton public final class Needs { public Needs(Plain plain) {} }"));
        assertThat(compilation).hadErrorContaining("Dependency must be @Wired or a @Provides product");
    }

    @Test
    void rejectsWiredInterface() {
        Compilation compilation = compile(
                registry(),
                JavaFileObjects.forSourceLines(
                        "test.Iface",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "@Wired public interface Iface {}"));
        assertThat(compilation).hadErrorContaining("@Wired requires a concrete class");
    }

    @Test
    void bindsInterfaceDependencyToItsSingleImplementation() {
        Compilation compilation = compile(
                registry(),
                JavaFileObjects.forSourceLines("test.Store", "package test;", "public interface Store {}"),
                JavaFileObjects.forSourceLines(
                        "test.SqlStore",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "@Wired @Singleton public final class SqlStore implements Store { public SqlStore() {} }"),
                JavaFileObjects.forSourceLines(
                        "test.Uses",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "@Wired @Singleton public final class Uses { public Uses(Store store) {} }"));
        assertThat(compilation).succeeded();
    }

    @Test
    void rejectsAmbiguousInterfaceDependency() {
        Compilation compilation = compile(
                registry(),
                JavaFileObjects.forSourceLines("test.Store", "package test;", "public interface Store {}"),
                JavaFileObjects.forSourceLines(
                        "test.SqlStore",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "@Wired @Singleton public final class SqlStore implements Store { public SqlStore() {} }"),
                JavaFileObjects.forSourceLines(
                        "test.FileStore",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "@Wired @Singleton public final class FileStore implements Store { public FileStore() {} }"),
                JavaFileObjects.forSourceLines(
                        "test.Uses",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "@Wired @Singleton public final class Uses { public Uses(Store store) {} }"));
        assertThat(compilation).hadErrorContaining("Ambiguous dependency test.Store");
    }

    @Test
    void acceptsUnresolvableOptionalDependency() {
        Compilation compilation = compile(
                registry(),
                JavaFileObjects.forSourceLines("test.Maybe", "package test;", "public interface Maybe {}"),
                JavaFileObjects.forSourceLines(
                        "test.Uses",
                        "package test;",
                        "import java.util.Optional;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "@Wired @Singleton public final class Uses { public Uses(Optional<Maybe> maybe) {} }"));
        assertThat(compilation).succeeded();
        // A singleton's optional nothing can fill resolves empty forever, worth a warning
        assertThat(compilation).hadWarningContaining("can never be present");
    }

    @Test
    void rejectsRawOptionalDependency() {
        Compilation compilation = compile(
                registry(),
                JavaFileObjects.forSourceLines(
                        "test.Uses",
                        "package test;",
                        "import java.util.Optional;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "@Wired @Singleton public final class Uses { public Uses(Optional maybe) {} }"));
        assertThat(compilation).hadErrorContaining("Optional dependencies need a concrete type argument");
    }

    @Test
    void acceptsLazyLoaderImplementation() {
        Compilation compilation = compile(
                registry(),
                JavaFileObjects.forSourceLines(
                        "test.Svc",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "import org.weftkit.wiring.Loader;",
                        "@Wired @Singleton(lazy = true) public final class Svc implements Loader {",
                        "  public Svc() {}",
                        "  public boolean load() { return true; }",
                        "}"));
        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("test.WeftWiring");
    }

    @Test
    void acceptsProvidesOnLazySingleton() {
        Compilation compilation = compile(
                registry(),
                JavaFileObjects.forSourceLines(
                        "test.Prov",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "import org.weftkit.wiring.Provides;",
                        "@Wired @Singleton(lazy = true) public final class Prov {",
                        "  public Prov() {}",
                        "  @Provides public String thing() { return \"\"; }",
                        "}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .contains("owner -> ((test.Prov) owner).thing()");
    }

    @Test
    void acceptsRequiresOnLazySingletonAndEmitsTheRequirement() {
        Compilation compilation = compile(
                registry(),
                settingsHolder(),
                JavaFileObjects.forSourceLines(
                        "test.Init",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "import org.weftkit.wiring.Initializes;",
                        "import org.weftkit.wiring.Loader;",
                        "@Wired @Singleton @Initializes(Settings.class)",
                        "public final class Init implements Loader {",
                        "  public Init() {}",
                        "  public boolean load() { Settings.LIMIT = 5; return true; }",
                        "}"),
                JavaFileObjects.forSourceLines(
                        "test.Svc",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "import org.weftkit.wiring.Requires;",
                        "import org.weftkit.wiring.Loader;",
                        "@Wired @Singleton(lazy = true) @Requires(Settings.class)",
                        "public final class Svc implements Loader {",
                        "  public Svc() {}",
                        "  public boolean load() { return Settings.LIMIT > 0; }",
                        "}"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .contains("Map.entry(test.Svc.class, List.<Class<?>>of(test.Init.class))");
    }

    @Test
    void acceptsInitializesOnLazySingletonAndEmitsTheRequirement() {
        Compilation compilation = compile(
                registry(),
                settingsHolder(),
                JavaFileObjects.forSourceLines(
                        "test.Init",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "import org.weftkit.wiring.Initializes;",
                        "import org.weftkit.wiring.Loader;",
                        "@Wired @Singleton(lazy = true) @Initializes(Settings.class)",
                        "public final class Init implements Loader {",
                        "  public Init() {}",
                        "  public boolean load() { Settings.LIMIT = 5; return true; }",
                        "}"),
                JavaFileObjects.forSourceLines(
                        "test.Svc",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "import org.weftkit.wiring.Requires;",
                        "@Wired @Singleton @Requires(Settings.class)",
                        "public final class Svc { public Svc() {} }"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .contains("Map.entry(test.Svc.class, List.<Class<?>>of(test.Init.class))");
    }

    @Test
    void bindsQualifiedDependencyToTaggedImplementation() {
        Compilation compilation = compile(
                registry(),
                JavaFileObjects.forSourceLines("test.Store", "package test;", "public interface Store {}"),
                JavaFileObjects.forSourceLines(
                        "test.SqlStore",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "import org.weftkit.wiring.Qualified;",
                        "@Wired @Singleton @Qualified(\"sql\")",
                        "public final class SqlStore implements Store { public SqlStore() {} }"),
                JavaFileObjects.forSourceLines(
                        "test.FileStore",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "import org.weftkit.wiring.Qualified;",
                        "@Wired @Singleton @Qualified(\"file\")",
                        "public final class FileStore implements Store { public FileStore() {} }"),
                JavaFileObjects.forSourceLines(
                        "test.Uses",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "import org.weftkit.wiring.Qualified;",
                        "@Wired @Singleton public final class Uses {",
                        "  public Uses(@Qualified(\"sql\") Store store) {}",
                        "}"));
        assertThat(compilation).succeeded();
    }

    @Test
    void allowsSameProductTypeWithDifferentQualifiers() {
        Compilation compilation = compile(
                registry(),
                JavaFileObjects.forSourceLines(
                        "test.Prov",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "import org.weftkit.wiring.Provides;",
                        "import org.weftkit.wiring.Qualified;",
                        "@Wired @Singleton public final class Prov {",
                        "  public Prov() {}",
                        "  @Provides public String primary() { return \"\"; }",
                        "  @Provides @Qualified(\"backup\") public String backup() { return \"\"; }",
                        "}"));
        assertThat(compilation).succeeded();
    }

    @Test
    void rejectsDuplicateProduct() {
        Compilation compilation = compile(
                registry(),
                JavaFileObjects.forSourceLines(
                        "test.Prov",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "import org.weftkit.wiring.Provides;",
                        "@Wired @Singleton public final class Prov {",
                        "  public Prov() {}",
                        "  @Provides public String primary() { return \"\"; }",
                        "  @Provides public String secondary() { return \"\"; }",
                        "}"));
        assertThat(compilation).hadErrorContaining("Product is already provided by");
    }

    @Test
    void bindsExternalInterfaceWithSingleImplementation() {
        Compilation compilation = compile(
                registry(),
                JavaFileObjects.forSourceLines(
                        "test.SqlStore",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "import org.weftkit.wiring.processor.fixture.ExternalStore;",
                        "@Wired @Singleton public final class SqlStore implements ExternalStore { public SqlStore() {} }"),
                JavaFileObjects.forSourceLines(
                        "test.Uses",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "import org.weftkit.wiring.processor.fixture.ExternalStore;",
                        "@Wired @Singleton public final class Uses { public Uses(ExternalStore store) {} }"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .contains("org.weftkit.wiring.processor.fixture.ExternalStore.class");
    }

    @Test
    void escapesQualifiersWithSpecialCharactersInGeneratedSource() {
        Compilation compilation = compile(
                registry(),
                JavaFileObjects.forSourceLines("test.Store", "package test;", "public interface Store {}"),
                JavaFileObjects.forSourceLines(
                        "test.SqlStore",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "import org.weftkit.wiring.Qualified;",
                        "@Wired @Singleton @Qualified(\"a\\\"b\\\\c\")",
                        "public final class SqlStore implements Store { public SqlStore() {} }"),
                JavaFileObjects.forSourceLines(
                        "test.Uses",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "import org.weftkit.wiring.Qualified;",
                        "@Wired @Singleton public final class Uses {",
                        "  public Uses(@Qualified(\"a\\\"b\\\\c\") Store store) {}",
                        "}"));
        // The generated registry is compiled in the same run, so a broken string literal would fail it
        assertThat(compilation).succeeded();
    }

    private static JavaFileObject externalStoreImplementations() {
        return JavaFileObjects.forSourceLines(
                "test.SqlStore",
                "package test;",
                "import org.weftkit.wiring.Wired;",
                "import org.weftkit.wiring.Singleton;",
                "import org.weftkit.wiring.processor.fixture.ExternalStore;",
                "@Wired @Singleton public final class SqlStore implements ExternalStore { public SqlStore() {} }");
    }

    private static JavaFileObject secondExternalStoreImplementation() {
        return JavaFileObjects.forSourceLines(
                "test.FileStore",
                "package test;",
                "import org.weftkit.wiring.Wired;",
                "import org.weftkit.wiring.Singleton;",
                "import org.weftkit.wiring.processor.fixture.ExternalStore;",
                "@Wired @Singleton public final class FileStore implements ExternalStore { public FileStore() {} }");
    }

    @Test
    void warnsWhenExternalInterfaceHasMultipleImplementationsForAPlainComponent() {
        Compilation compilation = compile(
                registry(),
                externalStoreImplementations(),
                secondExternalStoreImplementation(),
                JavaFileObjects.forSourceLines(
                        "test.Uses",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.processor.fixture.ExternalStore;",
                        "@Wired public final class Uses { public Uses(ExternalStore store) {} }"));
        assertThat(compilation).succeeded();
        assertThat(compilation).hadWarningContaining("has multiple @Wired implementations");
    }

    @Test
    void rejectsAmbiguousExternalInterfaceOnASingleton() {
        Compilation compilation = compile(
                registry(),
                externalStoreImplementations(),
                secondExternalStoreImplementation(),
                JavaFileObjects.forSourceLines(
                        "test.Uses",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "import org.weftkit.wiring.processor.fixture.ExternalStore;",
                        "@Wired @Singleton public final class Uses { public Uses(ExternalStore store) {} }"));
        assertThat(compilation)
                .hadErrorContaining("Ambiguous dependency org.weftkit.wiring.processor.fixture.ExternalStore");
    }

    @Test
    void resolvesExternalSingletonDependencyThroughADeclaredAmbientType() {
        Compilation compilation = compile(
                JavaFileObjects.forSourceLines(
                        "test.Reg",
                        "package test;",
                        "import org.weftkit.wiring.Registry;",
                        "import org.weftkit.wiring.processor.fixture.ExternalStore;",
                        "@Registry(ambient = ExternalStore.class) public final class Reg { public Reg() {} }"),
                JavaFileObjects.forSourceLines(
                        "test.Uses",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "import org.weftkit.wiring.processor.fixture.ExternalStore;",
                        "@Wired @Singleton public final class Uses { public Uses(ExternalStore store) {} }"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .contains("ambientTypes");
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .contains("org.weftkit.wiring.processor.fixture.ExternalStore.class");
    }

    @Test
    void bindsSingleImplementationEvenUnderAmbientCoverage() {
        Compilation compilation = compile(
                JavaFileObjects.forSourceLines(
                        "test.Reg",
                        "package test;",
                        "import org.weftkit.wiring.Registry;",
                        "import org.weftkit.wiring.processor.fixture.ExternalStore;",
                        "@Registry(ambient = ExternalStore.class) public final class Reg { public Reg() {} }"),
                externalStoreImplementations(),
                JavaFileObjects.forSourceLines(
                        "test.Uses",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "import org.weftkit.wiring.processor.fixture.ExternalStore;",
                        "@Wired @Singleton public final class Uses { public Uses(ExternalStore store) {} }"));
        assertThat(compilation).succeeded();
        // The binding backs an absent ambient value, the ambient layer wins when one is present
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .containsMatch("BINDINGS[\\s\\S]*ExternalStore.class,[\\s\\S]*test.SqlStore.class");
    }

    @Test
    void registryConstructorParametersAreDeclaredAmbientTypes() {
        Compilation compilation = compile(
                JavaFileObjects.forSourceLines(
                        "test.Reg",
                        "package test;",
                        "import org.weftkit.wiring.Registry;",
                        "import org.weftkit.wiring.processor.fixture.ExternalStore;",
                        "@Registry public final class Reg { public Reg(ExternalStore store) {} }"),
                JavaFileObjects.forSourceLines(
                        "test.Uses",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "import org.weftkit.wiring.processor.fixture.ExternalStore;",
                        "@Wired @Singleton public final class Uses { public Uses(ExternalStore store) {} }"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .contains("test.Reg.class");
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .contains("org.weftkit.wiring.processor.fixture.ExternalStore.class");
    }

    @Test
    void rejectsPrimitiveRegistryConstructorParameter() {
        Compilation compilation = compile(JavaFileObjects.forSourceLines(
                "test.Reg",
                "package test;",
                "import org.weftkit.wiring.Registry;",
                "@Registry public final class Reg { public Reg(int size) {} }"));
        assertThat(compilation).hadErrorContaining("@Registry constructor parameters must be classes or interfaces");
    }

    @Test
    void rejectsUndeclaredExternalSingletonDependency() {
        Compilation compilation = compile(
                registry(),
                JavaFileObjects.forSourceLines(
                        "test.Uses",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "import org.weftkit.wiring.processor.fixture.ExternalStore;",
                        "@Wired @Singleton public final class Uses { public Uses(ExternalStore store) {} }"));
        assertThat(compilation).hadErrorContaining("must be wired, a product, or a declared ambient type");
    }

    @Test
    void acceptsUndeclaredExternalDependencyOnAPlainComponent() {
        Compilation compilation = compile(
                registry(),
                JavaFileObjects.forSourceLines(
                        "test.Uses",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.processor.fixture.ExternalStore;",
                        "@Wired public final class Uses { public Uses(ExternalStore store) {} }"));
        assertThat(compilation).succeeded();
    }

    @Test
    void rejectsInjectingAComponentThatNeedsCreateArguments() {
        Compilation compilation = compile(
                registry(),
                JavaFileObjects.forSourceLines(
                        "test.Needs",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.processor.fixture.ExternalStore;",
                        "@Wired public final class Needs { public Needs(ExternalStore store) {} }"),
                JavaFileObjects.forSourceLines(
                        "test.Uses",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "@Wired public final class Uses { public Uses(Needs needs) {} }"));
        assertThat(compilation).hadErrorContaining("needs explicit create arguments and cannot be injected");
    }

    @Test
    void rejectsUnrelatedAmbientDeclarationsCoveringOneParameter() {
        Compilation compilation = compile(
                JavaFileObjects.forSourceLines("test.Api", "package test;", "public interface Api {}"),
                JavaFileObjects.forSourceLines(
                        "test.One", "package test;", "public final class One implements Api { public One() {} }"),
                JavaFileObjects.forSourceLines(
                        "test.Two", "package test;", "public final class Two implements Api { public Two() {} }"),
                JavaFileObjects.forSourceLines(
                        "test.Reg",
                        "package test;",
                        "import org.weftkit.wiring.Registry;",
                        "@Registry(ambient = {One.class, Two.class}) public final class Reg { public Reg() {} }"),
                JavaFileObjects.forSourceLines(
                        "test.Uses",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "@Wired @Singleton public final class Uses { public Uses(Api api) {} }"));
        assertThat(compilation).hadErrorContaining("Ambient declarations are ambiguous for test.Api");
    }

    @Test
    void acceptsSubtypeRelatedAmbientDeclarationsCoveringOneParameter() {
        Compilation compilation = compile(
                JavaFileObjects.forSourceLines("test.Api", "package test;", "public interface Api {}"),
                JavaFileObjects.forSourceLines(
                        "test.One", "package test;", "public class One implements Api { public One() {} }"),
                JavaFileObjects.forSourceLines(
                        "test.Sub", "package test;", "public final class Sub extends One { public Sub() {} }"),
                JavaFileObjects.forSourceLines(
                        "test.Reg",
                        "package test;",
                        "import org.weftkit.wiring.Registry;",
                        "@Registry(ambient = {One.class, Sub.class}) public final class Reg { public Reg() {} }"),
                JavaFileObjects.forSourceLines(
                        "test.Uses",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "@Wired @Singleton public final class Uses { public Uses(Api api) {} }"));
        assertThat(compilation).succeeded();
    }

    @Test
    void rejectsAmbiguousAmbientDeclarationsCoveringAProductType() {
        Compilation compilation = compile(
                JavaFileObjects.forSourceLines("test.Api", "package test;", "public interface Api {}"),
                JavaFileObjects.forSourceLines(
                        "test.One", "package test;", "public final class One implements Api { public One() {} }"),
                JavaFileObjects.forSourceLines(
                        "test.Two", "package test;", "public final class Two implements Api { public Two() {} }"),
                JavaFileObjects.forSourceLines(
                        "test.Reg",
                        "package test;",
                        "import org.weftkit.wiring.Registry;",
                        "@Registry(ambient = {One.class, Two.class}) public final class Reg { public Reg() {} }"),
                JavaFileObjects.forSourceLines(
                        "test.Owner",
                        "package test;",
                        "import org.weftkit.wiring.Provides;",
                        "import org.weftkit.wiring.Singleton;",
                        "import org.weftkit.wiring.Wired;",
                        "@Wired @Singleton public final class Owner {",
                        "  public Owner() {}",
                        "  @Provides public Api api() { return null; }",
                        "}"),
                JavaFileObjects.forSourceLines(
                        "test.Uses",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "@Wired public final class Uses { public Uses(Api api) {} }"));
        // The ambient layer outranks products, so the ambiguity is caught for product parameters too
        assertThat(compilation).hadErrorContaining("Ambient declarations are ambiguous for test.Api");
    }

    @Test
    void warnsOnASingletonOptionalDependencyNothingCanFill() {
        Compilation compilation = compile(
                registry(),
                JavaFileObjects.forSourceLines(
                        "test.Uses",
                        "package test;",
                        "import java.util.Optional;",
                        "import org.weftkit.wiring.Singleton;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.processor.fixture.ExternalStore;",
                        "@Wired @Singleton public final class Uses { public Uses(Optional<ExternalStore> store) {} }"));
        assertThat(compilation).succeeded();
        assertThat(compilation).hadWarningContaining("can never be present");
    }

    @Test
    void acceptsFeatureFlagsDependencyWithoutAWiredImplementation() {
        Compilation compilation = compile(
                registry(),
                JavaFileObjects.forSourceLines(
                        "test.Uses",
                        "package test;",
                        "import org.weftkit.wiring.Singleton;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.FeatureFlags;",
                        "@Wired @Singleton public final class Uses { public Uses(FeatureFlags flags) {} }"));
        // The loader declares FeatureFlags implicitly, so an ambient value may arrive
        assertThat(compilation).succeeded();
    }

    @Test
    void rejectsAmbientDeclarationShadowingAWiredComponent() {
        Compilation compilation = compile(
                JavaFileObjects.forSourceLines(
                        "test.Reg",
                        "package test;",
                        "import org.weftkit.wiring.Registry;",
                        "@Registry(ambient = Svc.class) public final class Reg { public Reg() {} }"),
                JavaFileObjects.forSourceLines(
                        "test.Svc",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "@Wired @Singleton public final class Svc { public Svc() {} }"));
        assertThat(compilation).hadErrorContaining("Ambient declaration shadows the @Wired component test.Svc");
    }

    private static JavaFileObject settingsHolder() {
        return JavaFileObjects.forSourceLines(
                "test.Settings",
                "package test;",
                "import org.weftkit.wiring.StaticHolder;",
                "@StaticHolder public final class Settings {",
                "  public static int LIMIT = 3;",
                "}");
    }

    @Test
    void rejectsUndeclaredHolderAccessDuringLoad() {
        Compilation compilation = compile(
                registry(),
                settingsHolder(),
                JavaFileObjects.forSourceLines(
                        "test.Svc",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "import org.weftkit.wiring.Loader;",
                        "@Wired @Singleton public final class Svc implements Loader {",
                        "  public Svc() {}",
                        "  public boolean load() { return Settings.LIMIT > 0; }",
                        "}"));
        assertThat(compilation)
                .hadErrorContaining("Static holder is accessed during load without @Requires: test.Settings");
    }

    @Test
    void acceptsDeclaredHolderAccessDuringLoad() {
        Compilation compilation = compile(
                registry(),
                settingsHolder(),
                JavaFileObjects.forSourceLines(
                        "test.Init",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "import org.weftkit.wiring.Initializes;",
                        "import org.weftkit.wiring.Loader;",
                        "@Wired @Singleton @Initializes(Settings.class)",
                        "public final class Init implements Loader {",
                        "  public Init() {}",
                        "  public boolean load() { Settings.LIMIT = 5; return true; }",
                        "}"),
                JavaFileObjects.forSourceLines(
                        "test.Svc",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "import org.weftkit.wiring.Requires;",
                        "import org.weftkit.wiring.Loader;",
                        "@Wired @Singleton @Requires(Settings.class)",
                        "public final class Svc implements Loader {",
                        "  public Svc() {}",
                        "  public boolean load() { return Settings.LIMIT > 0; }",
                        "}"));
        assertThat(compilation).succeeded();
    }

    @Test
    void graphIncludesStaticHolders() {
        Compilation compilation = compile(
                registry(),
                settingsHolder(),
                JavaFileObjects.forSourceLines(
                        "test.Init",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "import org.weftkit.wiring.Initializes;",
                        "import org.weftkit.wiring.Loader;",
                        "@Wired @Singleton @Initializes(Settings.class)",
                        "public final class Init implements Loader {",
                        "  public Init() {}",
                        "  public boolean load() { Settings.LIMIT = 5; return true; }",
                        "}"),
                JavaFileObjects.forSourceLines(
                        "test.Svc",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "import org.weftkit.wiring.Requires;",
                        "@Wired @Singleton @Requires(Settings.class)",
                        "public final class Svc { public Svc() {} }"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedFile(StandardLocation.SOURCE_OUTPUT, "test", "weftkit-graph.dot")
                .contentsAsUtf8String()
                .contains("\"test.Svc\" -> \"test.Settings\" [style=dashed]");
        assertThat(compilation)
                .generatedFile(StandardLocation.SOURCE_OUTPUT, "test", "weftkit-graph.dot")
                .contentsAsUtf8String()
                .contains("\"test.Settings\" -> \"test.Init\"");
    }

    @Test
    void graphIncludesAmbientRoots() {
        Compilation compilation = compile(
                JavaFileObjects.forSourceLines(
                        "test.Reg",
                        "package test;",
                        "import org.weftkit.wiring.Registry;",
                        "import org.weftkit.wiring.processor.fixture.ExternalStore;",
                        "@Registry(ambient = ExternalStore.class) public final class Reg { public Reg() {} }"),
                JavaFileObjects.forSourceLines(
                        "test.Uses",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "import org.weftkit.wiring.processor.fixture.ExternalStore;",
                        "@Wired @Singleton public final class Uses { public Uses(ExternalStore store) {} }"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedFile(StandardLocation.SOURCE_OUTPUT, "test", "weftkit-graph.dot")
                .contentsAsUtf8String()
                .contains("shape=parallelogram");
        assertThat(compilation)
                .generatedFile(StandardLocation.SOURCE_OUTPUT, "test", "weftkit-graph.dot")
                .contentsAsUtf8String()
                .contains("\"test.Uses\" -> \"org.weftkit.wiring.processor.fixture.ExternalStore\"");
    }

    @Test
    void ignoresHolderAccessOutsideTheLoadWindow() {
        Compilation compilation = compile(
                registry(),
                settingsHolder(),
                JavaFileObjects.forSourceLines(
                        "test.Cmd",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "@Wired public final class Cmd {",
                        "  public Cmd() {}",
                        "  public int limit() { return Settings.LIMIT; }",
                        "}"));
        assertThat(compilation).succeeded();
    }

    @Test
    void ignoresLoadHelperOnNonLoaderComponent() {
        Compilation compilation = compile(
                registry(),
                settingsHolder(),
                JavaFileObjects.forSourceLines(
                        "test.Cmd",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "@Wired public final class Cmd {",
                        "  public Cmd() {}",
                        "  public int load() { return Settings.LIMIT; }",
                        "}"));
        assertThat(compilation).succeeded();
    }

    @Test
    void rejectsInitializesTargetWithoutStaticHolder() {
        Compilation compilation = compile(
                registry(),
                JavaFileObjects.forSourceLines(
                        "test.Plain", "package test;", "public final class Plain { public static int VALUE; }"),
                JavaFileObjects.forSourceLines(
                        "test.Init",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "import org.weftkit.wiring.Initializes;",
                        "import org.weftkit.wiring.Loader;",
                        "@Wired @Singleton @Initializes(Plain.class)",
                        "public final class Init implements Loader {",
                        "  public Init() {}",
                        "  public boolean load() { return true; }",
                        "}"));
        assertThat(compilation).hadErrorContaining("Holder must be annotated with @StaticHolder: test.Plain");
    }

    @Test
    void rejectsRequiresTargetWithoutStaticHolder() {
        Compilation compilation = compile(
                registry(),
                JavaFileObjects.forSourceLines(
                        "test.Plain", "package test;", "public final class Plain { public static int VALUE; }"),
                JavaFileObjects.forSourceLines(
                        "test.Svc",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "import org.weftkit.wiring.Requires;",
                        "@Wired @Singleton @Requires(Plain.class)",
                        "public final class Svc { public Svc() {} }"));
        assertThat(compilation).hadErrorContaining("Holder must be annotated with @StaticHolder: test.Plain");
    }

    @Test
    void wiresPackagePrivateComponentThroughItsPackageFragment() {
        Compilation compilation = compile(
                registry(),
                JavaFileObjects.forSourceLines("test.Store", "package test;", "public interface Store {}"),
                JavaFileObjects.forSourceLines(
                        "test.storage.SqlStore",
                        "package test.storage;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "@Wired @Singleton final class SqlStore implements test.Store { SqlStore() {} }"),
                JavaFileObjects.forSourceLines(
                        "test.Uses",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "@Wired @Singleton public final class Uses { public Uses(Store store) {} }"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("test.storage.WeftWiring")
                .contentsAsUtf8String()
                .contains("Map.entry(test.storage.SqlStore.class, arguments -> new test.storage.SqlStore())");
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .contains("Registries.merge(Map.ofEntries(");
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .contains("Registries.type(FACTORIES, \"test.storage.SqlStore\")");
    }

    @Test
    void wiresPackagePrivateComponentInTheRegistryPackageDirectly() {
        Compilation compilation = compile(
                registry(),
                JavaFileObjects.forSourceLines(
                        "test.Hidden",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "@Wired @Singleton final class Hidden { Hidden() {} }"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("test.WeftWiring")
                .contentsAsUtf8String()
                .contains("test.Hidden.class");
    }

    @Test
    void acceptsPackageVisibleGetterOnHiddenOwner() {
        Compilation compilation = compile(
                registry(),
                JavaFileObjects.forSourceLines(
                        "test.parts.Gears",
                        "package test.parts;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "import org.weftkit.wiring.Provides;",
                        "@Wired @Singleton final class Gears {",
                        "  Gears() {}",
                        "  @Provides String gear() { return \"\"; }",
                        "}"),
                JavaFileObjects.forSourceLines(
                        "test.parts.GearUser",
                        "package test.parts;",
                        "import org.weftkit.wiring.Wired;",
                        "@Wired public final class GearUser { public GearUser(String gear) {} }"));
        assertThat(compilation).succeeded();
    }

    @Test
    void rejectsPackageVisibleGetterOnPublicOwner() {
        Compilation compilation = compile(
                registry(),
                JavaFileObjects.forSourceLines(
                        "test.Prov",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "import org.weftkit.wiring.Provides;",
                        "@Wired @Singleton public final class Prov {",
                        "  public Prov() {}",
                        "  @Provides String thing() { return \"\"; }",
                        "}"));
        assertThat(compilation).hadErrorContaining("public unless the owner is hidden");
    }

    @Test
    void ignoresQualifiedOnFields() {
        Compilation compilation = compile(
                registry(),
                JavaFileObjects.forSourceLines(
                        "test.Holder",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Qualified;",
                        "@Wired public final class Holder {",
                        "  @Qualified(\"spare\") private String note;",
                        "  public Holder() {}",
                        "}"));
        assertThat(compilation).succeeded();
    }

    @Test
    void wiresPublicFacadeWithPackagePrivateDependency() {
        Compilation compilation = compile(
                registry(),
                JavaFileObjects.forSourceLines(
                        "test.storage.Pool",
                        "package test.storage;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "@Wired @Singleton final class Pool { Pool() {} }"),
                JavaFileObjects.forSourceLines(
                        "test.storage.Facade",
                        "package test.storage;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "@Wired @Singleton public final class Facade { public Facade(Pool pool) {} }"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("test.storage.WeftWiring")
                .contentsAsUtf8String()
                .contains("new test.storage.Facade((test.storage.Pool) arguments[0])");
    }

    @Test
    void exposesProductOfPackagePrivateOwnerThroughItsFragment() {
        Compilation compilation = compile(
                registry(),
                JavaFileObjects.forSourceLines(
                        "test.storage.Prov",
                        "package test.storage;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "import org.weftkit.wiring.Provides;",
                        "@Wired @Singleton final class Prov {",
                        "  Prov() {}",
                        "  @Provides public String token() { return \"\"; }",
                        "}"),
                JavaFileObjects.forSourceLines(
                        "test.Uses",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "@Wired @Singleton public final class Uses { public Uses(String token) {} }"));
        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("test.storage.WeftWiring")
                .contentsAsUtf8String()
                .contains("owner -> ((test.storage.Prov) owner).token()");
    }

    @Test
    void rejectsPrivateNestedComponent() {
        Compilation compilation = compile(
                registry(),
                JavaFileObjects.forSourceLines(
                        "test.Outer",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "public final class Outer {",
                        "  @Wired private static final class Inner { public Inner() {} }",
                        "}"));
        assertThat(compilation)
                .hadErrorContaining("@Wired classes must be top-level or static nested and at least package visible");
    }

    @Test
    void rejectsFragmentNameCollision() {
        Compilation compilation = compile(
                registry(),
                JavaFileObjects.forSourceLines(
                        "test.storage.WeftWiring", "package test.storage;", "public final class WeftWiring {}"),
                JavaFileObjects.forSourceLines(
                        "test.storage.Hidden",
                        "package test.storage;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "@Wired @Singleton final class Hidden { Hidden() {} }"));
        assertThat(compilation)
                .hadErrorContaining("weftkit cannot generate test.storage.WeftWiring because the class already exists");
    }

    @Test
    void rejectsDuplicateConstructorParameterType() {
        Compilation compilation = compile(
                registry(),
                JavaFileObjects.forSourceLines(
                        "test.Dep",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "@Wired @Singleton public final class Dep { public Dep() {} }"),
                JavaFileObjects.forSourceLines(
                        "test.Two",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "@Wired @Singleton public final class Two { public Two(Dep a, Dep b) {} }"));
        assertThat(compilation).hadErrorContaining("Duplicate constructor parameter type");
    }
}
