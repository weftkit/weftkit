package org.weftkit.wiring.processor;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import org.junit.jupiter.api.Test;

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

    @Test
    void warnsWhenExternalInterfaceHasMultipleImplementations() {
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
                        "test.FileStore",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "import org.weftkit.wiring.processor.fixture.ExternalStore;",
                        "@Wired @Singleton public final class FileStore implements ExternalStore { public FileStore() {} }"),
                JavaFileObjects.forSourceLines(
                        "test.Uses",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "import org.weftkit.wiring.processor.fixture.ExternalStore;",
                        "@Wired @Singleton public final class Uses { public Uses(ExternalStore store) {} }"));
        assertThat(compilation).succeeded();
        assertThat(compilation).hadWarningContaining("has multiple @Wired implementations");
    }

    @Test
    void leavesAmbiguousExternalInterfaceUnbound() {
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
                        "test.FileStore",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "import org.weftkit.wiring.processor.fixture.ExternalStore;",
                        "@Wired @Singleton public final class FileStore implements ExternalStore { public FileStore() {} }"),
                JavaFileObjects.forSourceLines(
                        "test.Uses",
                        "package test;",
                        "import org.weftkit.wiring.Wired;",
                        "import org.weftkit.wiring.Singleton;",
                        "import org.weftkit.wiring.processor.fixture.ExternalStore;",
                        "@Wired @Singleton public final class Uses { public Uses(ExternalStore store) {} }"));
        assertThat(compilation).succeeded();
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
