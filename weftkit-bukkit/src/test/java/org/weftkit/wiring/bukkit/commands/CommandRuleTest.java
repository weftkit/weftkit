package org.weftkit.wiring.bukkit.commands;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;
import org.weftkit.wiring.processor.WiredProcessor;

class CommandRuleTest {

    private static Compilation compile(JavaFileObject command) {
        return javac().withProcessors(new WiredProcessor())
                .compile(
                        JavaFileObjects.forSourceLines(
                                "test.Reg",
                                "package test;",
                                "import org.weftkit.wiring.Registry;",
                                "@Registry public final class Reg { public Reg() {} }"),
                        command);
    }

    @Test
    void acceptsAWellFormedCommand() {
        Compilation compilation = compile(JavaFileObjects.forSourceLines(
                "test.Home",
                "package test;",
                "import org.weftkit.wiring.Wired;",
                "import org.weftkit.wiring.Singleton;",
                "import org.weftkit.wiring.bukkit.commands.CommandHandler;",
                "import org.bukkit.command.Command;",
                "import org.bukkit.command.CommandExecutor;",
                "import org.bukkit.command.CommandSender;",
                "@Wired @Singleton @CommandHandler(\"home\") public final class Home implements CommandExecutor {",
                "  public Home() {}",
                "  public boolean onCommand(CommandSender s, Command c, String l, String[] a) { return true; }",
                "}"));
        assertThat(compilation).succeeded();
    }

    @Test
    void rejectsCommandThatDoesNotImplementExecutor() {
        Compilation compilation = compile(JavaFileObjects.forSourceLines(
                "test.NotExecutor",
                "package test;",
                "import org.weftkit.wiring.Wired;",
                "import org.weftkit.wiring.Singleton;",
                "import org.weftkit.wiring.bukkit.commands.CommandHandler;",
                "@Wired @Singleton @CommandHandler(\"home\") public final class NotExecutor {",
                "  public NotExecutor() {}",
                "}"));
        assertThat(compilation).hadErrorContaining("@CommandHandler components must implement CommandExecutor");
    }

    @Test
    void rejectsBlankCommandName() {
        Compilation compilation = compile(JavaFileObjects.forSourceLines(
                "test.Blank",
                "package test;",
                "import org.weftkit.wiring.Wired;",
                "import org.weftkit.wiring.Singleton;",
                "import org.weftkit.wiring.bukkit.commands.CommandHandler;",
                "import org.bukkit.command.Command;",
                "import org.bukkit.command.CommandExecutor;",
                "import org.bukkit.command.CommandSender;",
                "@Wired @Singleton @CommandHandler(\"  \") public final class Blank implements CommandExecutor {",
                "  public Blank() {}",
                "  public boolean onCommand(CommandSender s, Command c, String l, String[] a) { return true; }",
                "}"));
        assertThat(compilation).hadErrorContaining("@CommandHandler needs a command name");
    }
}
