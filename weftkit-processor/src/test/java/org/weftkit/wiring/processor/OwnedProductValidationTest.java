package org.weftkit.wiring.processor;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;

class OwnedProductValidationTest {
    private static Compilation compile(String consumerAnnotation, String returnType) {
        return javac().withProcessors(new WiredProcessor())
                .compile(
                        JavaFileObjects.forSourceLines(
                                "test.Reg", "package test;", "@org.weftkit.wiring.Registry public class Reg {}"),
                        JavaFileObjects.forSourceLines(
                                "test.Provider",
                                "package test;",
                                "import org.weftkit.wiring.*;",
                                "@Wired @Singleton public class Provider {",
                                " public Provider() {}",
                                " @Provides(owned = true) public " + returnType + " resource() { return null; }",
                                "}"),
                        JavaFileObjects.forSourceLines(
                                "test.Consumer",
                                "package test;",
                                "import org.weftkit.wiring.*;",
                                "@Wired " + consumerAnnotation + " public class Consumer {",
                                " public Consumer(" + returnType + " resource) {}",
                                "}"));
    }

    @Test
    void singletonConsumersCanInjectOwnedProducts() {
        assertThat(compile("@Singleton", "java.lang.AutoCloseable")).succeeded();
        assertThat(compile("@Singleton(lazy = true)", "java.lang.AutoCloseable"))
                .succeeded();
    }

    @Test
    void prototypeConsumersAreRejected() {
        assertThat(compile("", "java.lang.AutoCloseable"))
                .hadErrorContaining("Owned products are only injectable into singletons");
    }

    @Test
    void ownedProductsMustBeCloseable() {
        assertThat(compile("@Singleton", "String"))
                .hadErrorContaining("@Provides(owned = true) must return an AutoCloseable");
    }
}
