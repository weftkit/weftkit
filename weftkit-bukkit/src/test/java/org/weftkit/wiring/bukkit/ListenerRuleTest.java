package org.weftkit.wiring.bukkit;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;
import org.weftkit.wiring.processor.WiredProcessor;

class ListenerRuleTest {

    private static Compilation compile(JavaFileObject listener) {
        return javac().withProcessors(new WiredProcessor())
                .compile(
                        JavaFileObjects.forSourceLines(
                                "test.Reg",
                                "package test;",
                                "import org.weftkit.wiring.Registry;",
                                "@Registry public final class Reg { public Reg() {} }"),
                        listener);
    }

    @Test
    void acceptsAWellFormedListener() {
        Compilation compilation = compile(JavaFileObjects.forSourceLines(
                "test.Join",
                "package test;",
                "import org.weftkit.wiring.Wired;",
                "import org.weftkit.wiring.Singleton;",
                "import org.bukkit.event.EventHandler;",
                "import org.bukkit.event.Listener;",
                "import org.bukkit.event.player.PlayerJoinEvent;",
                "@Wired @Singleton public final class Join implements Listener {",
                "  public Join() {}",
                "  @EventHandler public void onJoin(PlayerJoinEvent event) {}",
                "}"));
        assertThat(compilation).succeeded();
    }

    @Test
    void rejectsListenerWithoutHandlers() {
        Compilation compilation = compile(JavaFileObjects.forSourceLines(
                "test.Empty",
                "package test;",
                "import org.weftkit.wiring.Wired;",
                "import org.weftkit.wiring.Singleton;",
                "import org.bukkit.event.Listener;",
                "@Wired @Singleton public final class Empty implements Listener { public Empty() {} }"));
        assertThat(compilation).hadErrorContaining("Listeners need at least one @EventHandler method");
    }

    @Test
    void rejectsNonPublicHandler() {
        Compilation compilation = compile(JavaFileObjects.forSourceLines(
                "test.Hidden",
                "package test;",
                "import org.weftkit.wiring.Wired;",
                "import org.weftkit.wiring.Singleton;",
                "import org.bukkit.event.EventHandler;",
                "import org.bukkit.event.Listener;",
                "import org.bukkit.event.player.PlayerJoinEvent;",
                "@Wired @Singleton public final class Hidden implements Listener {",
                "  public Hidden() {}",
                "  @EventHandler void onJoin(PlayerJoinEvent event) {}",
                "}"));
        assertThat(compilation).hadErrorContaining("Event handlers must be public");
    }

    @Test
    void rejectsHandlerWithWrongParameterCount() {
        Compilation compilation = compile(JavaFileObjects.forSourceLines(
                "test.NoArg",
                "package test;",
                "import org.weftkit.wiring.Wired;",
                "import org.weftkit.wiring.Singleton;",
                "import org.bukkit.event.EventHandler;",
                "import org.bukkit.event.Listener;",
                "@Wired @Singleton public final class NoArg implements Listener {",
                "  public NoArg() {}",
                "  @EventHandler public void onJoin() {}",
                "}"));
        assertThat(compilation).hadErrorContaining("Event handlers take exactly one event parameter");
    }

    @Test
    void rejectsHandlerWithNonEventParameter() {
        Compilation compilation = compile(JavaFileObjects.forSourceLines(
                "test.Wrong",
                "package test;",
                "import org.weftkit.wiring.Wired;",
                "import org.weftkit.wiring.Singleton;",
                "import org.bukkit.event.EventHandler;",
                "import org.bukkit.event.Listener;",
                "@Wired @Singleton public final class Wrong implements Listener {",
                "  public Wrong() {}",
                "  @EventHandler public void onJoin(String event) {}",
                "}"));
        assertThat(compilation).hadErrorContaining("Event handler parameter must be a Bukkit event");
    }
}
