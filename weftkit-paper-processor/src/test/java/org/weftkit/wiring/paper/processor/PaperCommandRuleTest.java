package org.weftkit.wiring.paper.processor;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.ArrayList;
import java.util.List;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;
import org.weftkit.wiring.processor.WiredProcessor;

class PaperCommandRuleTest {

    private static final String BASIC_BODY =
            "  public void execute(io.papermc.paper.command.brigadier.CommandSourceStack s, String[] a) {}";

    private static final String TREE_BODY =
            "  public com.mojang.brigadier.builder.LiteralArgumentBuilder<io.papermc.paper.command.brigadier.CommandSourceStack>"
                    + " tree(String l) { return io.papermc.paper.command.brigadier.Commands.literal(l); }";

    @Test
    void acceptsABasicCommand() {
        assertThat(compile("@PaperCommand(value = \"home\", aliases = \"h\")", "BasicCommand", BASIC_BODY))
                .succeeded();
    }

    @Test
    void acceptsACommandTree() {
        assertThat(compile("@PaperCommand(\"warp\")", "CommandTree", TREE_BODY)).succeeded();
    }

    @Test
    void rejectsAComponentImplementingNeither() {
        assertThat(compile("@PaperCommand(\"home\")", null, ""))
                .hadErrorContaining("must implement exactly one of BasicCommand or CommandTree");
    }

    @Test
    void rejectsAComponentImplementingBoth() {
        assertThat(compile("@PaperCommand(\"home\")", "BasicCommand, CommandTree", BASIC_BODY + "\n" + TREE_BODY))
                .hadErrorContaining("must implement exactly one of BasicCommand or CommandTree");
    }

    @Test
    void rejectsABlankLabel() {
        assertThat(compile("@PaperCommand(\" \")", "BasicCommand", BASIC_BODY))
                .hadErrorContaining("@PaperCommand label must not be blank");
    }

    @Test
    void rejectsABlankAlias() {
        assertThat(compile("@PaperCommand(value = \"home\", aliases = \"\")", "BasicCommand", BASIC_BODY))
                .hadErrorContaining("aliases must not be blank");
    }

    @Test
    void rejectsAnAliasEqualToTheLabel() {
        assertThat(compile("@PaperCommand(value = \"home\", aliases = \"home\")", "BasicCommand", BASIC_BODY))
                .hadErrorContaining("aliases must be distinct and differ from the label");
    }

    @Test
    void rejectsALabelWithWhitespace() {
        assertThat(compile("@PaperCommand(\"my warp\")", "BasicCommand", BASIC_BODY))
                .hadErrorContaining("label must not contain whitespace or ':'");
    }

    @Test
    void rejectsAnAliasWithAColon() {
        assertThat(compile("@PaperCommand(value = \"home\", aliases = \"my:home\")", "BasicCommand", BASIC_BODY))
                .hadErrorContaining("aliases must not contain whitespace or ':'");
    }

    @Test
    void rejectsALabelWithALeadingSlash() {
        assertThat(compile("@PaperCommand(\"/warp\")", "BasicCommand", BASIC_BODY))
                .hadErrorContaining("label must not start with '/'");
    }

    @Test
    void rejectsAnUppercaseAlias() {
        assertThat(compile("@PaperCommand(value = \"home\", aliases = \"Home\")", "BasicCommand", BASIC_BODY))
                .hadErrorContaining("aliases must be lowercase");
    }

    @Test
    void rejectsRepeatedAliases() {
        assertThat(compile("@PaperCommand(value = \"home\", aliases = {\"h\", \"h\"})", "BasicCommand", BASIC_BODY))
                .hadErrorContaining("aliases must be distinct");
    }

    @Test
    void rejectsALabelTakenByAnotherCommand() {
        Compilation compilation = javac().withProcessors(new WiredProcessor())
                .compile(
                        JavaFileObjects.forSourceLines(
                                "test.Reg",
                                "package test;",
                                "import org.weftkit.wiring.Registry;",
                                "@Registry public final class Reg { public Reg() {} }"),
                        command("Warp", "@PaperCommand(\"warp\")"),
                        command("Home", "@PaperCommand(value = \"home\", aliases = \"warp\")"));
        assertThat(compilation).hadErrorContaining("@PaperCommand label \"warp\" is already taken by test.Warp");
    }

    @Test
    void rejectsAFlaggedCommandSharingALabelWithAnUnflaggedOne() {
        assertThat(compileCommands(
                        command("Warp", "@PaperCommand(\"warp\")"),
                        command("NewWarp", "@PaperCommand(\"warp\") @org.weftkit.wiring.FeatureFlag(\"new-warp\")")))
                .hadErrorContaining("@PaperCommand label \"warp\" is already taken by test.Warp");
    }

    @Test
    void rejectsCommandsOfTheSameFlagSharingALabel() {
        assertThat(compileCommands(
                        command("Warp", "@PaperCommand(\"warp\") @org.weftkit.wiring.FeatureFlag(\"warps\")"),
                        command("NewWarp", "@PaperCommand(\"warp\") @org.weftkit.wiring.FeatureFlag(\"warps\")")))
                .hadErrorContaining("@PaperCommand label \"warp\" is already taken by test.");
    }

    @Test
    void acceptsCommandsOfDifferentFlagsSharingALabel() {
        assertThat(compileCommands(
                        command("Warp", "@PaperCommand(\"warp\") @org.weftkit.wiring.FeatureFlag(\"old-warp\")"),
                        command("NewWarp", "@PaperCommand(\"warp\") @org.weftkit.wiring.FeatureFlag(\"new-warp\")")))
                .succeeded();
    }

    private static Compilation compileCommands(JavaFileObject... commands) {
        List<JavaFileObject> sources = new ArrayList<>(List.of(JavaFileObjects.forSourceLines(
                "test.Reg",
                "package test;",
                "import org.weftkit.wiring.Registry;",
                "@Registry public final class Reg { public Reg() {} }")));
        sources.addAll(List.of(commands));
        return javac().withProcessors(new WiredProcessor()).compile(sources);
    }

    @Test
    void leavesAnUnresolvableAliasToJavac() {
        Compilation compilation =
                compile("@PaperCommand(value = \"home\", aliases = Labels.HOME)", "BasicCommand", BASIC_BODY);
        assertThat(compilation).hadErrorCount(1);
        assertThat(compilation).hadErrorContaining("Labels");
    }

    @Test
    void rejectsANonSingleton() {
        assertThat(compile("@PaperCommand(\"home\")", "BasicCommand", BASIC_BODY, ""))
                .hadErrorContaining("must be a @Singleton");
    }

    @Test
    void acceptsALazySingleton() {
        assertThat(compile("@PaperCommand(\"home\")", "BasicCommand", BASIC_BODY, "@Singleton(lazy = true) "))
                .succeeded();
    }

    @Test
    void rejectsACombinationWithCommandHandler() {
        assertThat(compile(
                        "@PaperCommand(\"home\") @org.weftkit.wiring.bukkit.commands.CommandHandler(\"home\")",
                        "BasicCommand",
                        BASIC_BODY))
                .hadErrorContaining("Use either @PaperCommand or @CommandHandler, not both");
    }

    private static JavaFileObject command(String name, String annotation) {
        return JavaFileObjects.forSourceLines(
                "test." + name,
                "package test;",
                "import org.weftkit.wiring.Wired;",
                "import org.weftkit.wiring.Singleton;",
                "import io.papermc.paper.command.brigadier.BasicCommand;",
                "import org.weftkit.wiring.paper.commands.PaperCommand;",
                "@Wired @Singleton " + annotation + " public final class " + name + " implements BasicCommand {",
                "  public " + name + "() {}",
                BASIC_BODY,
                "}");
    }

    private static Compilation compile(String annotations, String interfaces, String body) {
        return compile(annotations, interfaces, body, "@Singleton ");
    }

    private static Compilation compile(String annotations, String interfaces, String body, String scope) {
        return javac().withProcessors(new WiredProcessor())
                .compile(
                        JavaFileObjects.forSourceLines(
                                "test.Reg",
                                "package test;",
                                "import org.weftkit.wiring.Registry;",
                                "@Registry public final class Reg { public Reg() {} }"),
                        JavaFileObjects.forSourceLines(
                                "test.Cmd",
                                "package test;",
                                "import org.weftkit.wiring.Wired;",
                                "import org.weftkit.wiring.Singleton;",
                                "import io.papermc.paper.command.brigadier.BasicCommand;",
                                "import org.weftkit.wiring.paper.commands.CommandTree;",
                                "import org.weftkit.wiring.paper.commands.PaperCommand;",
                                "@Wired " + scope + annotations + " public final class Cmd"
                                        + (interfaces == null ? "" : " implements " + interfaces) + " {",
                                "  public Cmd() {}",
                                body,
                                "}"));
    }
}
