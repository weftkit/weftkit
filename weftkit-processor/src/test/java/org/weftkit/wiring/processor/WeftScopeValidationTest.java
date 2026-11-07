package org.weftkit.wiring.processor;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;

class WeftScopeValidationTest {
    private static Compilation compile(String annotations, String qualifier) {
        return javac().withProcessors(new WiredProcessor())
                .compile(
                        JavaFileObjects.forSourceLines(
                                "test.Reg", "package test;", "@org.weftkit.wiring.Registry public class Reg {}"),
                        JavaFileObjects.forSourceLines(
                                "org.weftkit.wiring.runtime.WeftScope",
                                "package org.weftkit.wiring.runtime;",
                                "public final class WeftScope {}"),
                        JavaFileObjects.forSourceLines(
                                "test.Owner",
                                "package test;",
                                "import org.weftkit.wiring.*;",
                                "import org.weftkit.wiring.runtime.WeftScope;",
                                "@Wired " + annotations + " public class Owner {",
                                "  public Owner(" + qualifier + " WeftScope scope) {}",
                                "}"));
    }

    @Test
    void eagerAndLazySingletonsCanInjectOwnership() {
        assertThat(compile("@Singleton", "")).succeeded();
        assertThat(compile("@Singleton(lazy = true)", "")).succeeded();
    }

    @Test
    void prototypesCannotInjectOwnership() {
        assertThat(compile("", "")).hadErrorContaining("WeftScope is only injectable into singletons");
    }

    @Test
    void scopeCannotCarryAQualifier() {
        assertThat(compile("@Singleton", "@Qualified(\"other\")")).hadErrorContaining("WeftScope cannot be qualified");
    }
}
