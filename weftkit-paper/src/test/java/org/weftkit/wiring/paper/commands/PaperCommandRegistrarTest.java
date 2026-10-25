package org.weftkit.wiring.paper.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

class PaperCommandRegistrarTest {

    private final List<Object[]> registrations = new ArrayList<>();

    private final Set<String> taken = new HashSet<>();

    private final List<String> warnings = new ArrayList<>();

    private PaperCommandRegistrar paper;

    private static final Map<Class<?>, PaperCommand> META = Map.of(
            Home.class, command("home", "Teleport home", "h"),
            NewHome.class, command("house", "", "home"),
            Warp.class, command("warp", ""),
            Misrooted.class, command("misrooted", ""),
            Broken.class, command("broken", ""),
            Cached.class, command("cached", ""),
            Restricted.class, command("restricted", ""));

    @Test
    void registersABasicCommandWithItsMetadata() {
        registerAll(List.of(new Home()), List.of());
        Object[] registration = registrations.get(0);
        assertEquals("home", registration[0]);
        assertEquals("Teleport home", registration[1]);
        assertEquals(List.of("h"), registration[2]);
    }

    @Test
    void registersATreeWithItsMetadata() {
        registerAll(List.of(), List.of(new Warp()));
        Object[] registration = registrations.get(0);
        assertEquals("warp", ((LiteralCommandNode<?>) registration[0]).getLiteral());
        assertNull(registration[1]);
        assertEquals(List.of(), registration[2]);
    }

    @Test
    void skipsATreeRootedAtAnotherLabel() {
        registerAll(List.of(), List.of(new Misrooted()));
        assertTrue(registrations.isEmpty());
    }

    @Test
    void warnsAboutLabelsTakenByOtherCommands() {
        taken.add("h");
        registerAll(List.of(new Home()), List.of());
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("[h]"));
    }

    @Test
    void leavesACommandClaimingAnEarlierLabelUnregistered() {
        registerAll(List.of(new Home(), new NewHome()), List.of(new Warp()));
        assertEquals(2, registrations.size());
        assertEquals("home", registrations.get(0)[0]);
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("[home]"));
        assertTrue(warnings.get(0).contains("already claimed by " + Home.class.getName()));
    }

    @Test
    void staysQuietWhenEveryLabelRegisters() {
        registerAll(List.of(new Home()), List.of(new Warp()));
        assertTrue(warnings.isEmpty());
    }

    @Test
    void keepsRegisteringAfterAFailingCommand() {
        registerAll(List.of(), List.of(new Broken(), new Warp()));
        assertEquals(1, registrations.size());
        assertEquals("warp", ((LiteralCommandNode<?>) registrations.get(0)[0]).getLiteral());
    }

    @Test
    void leavesACachedTreeUntouchedAcrossRebuilds() {
        Cached cached = new Cached();
        registerAll(List.of(), List.of(cached));
        registerAll(List.of(), List.of(cached));
        assertTrue(cached.builder.getRequirement().test(source()));
        paper.deactivate();
        assertTrue(cached.builder.getRequirement().test(source()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void keepsTheTreeChildren() {
        registerAll(List.of(), List.of(new Cached()));
        LiteralCommandNode<CommandSourceStack> node =
                (LiteralCommandNode<CommandSourceStack>) registrations.get(0)[0];
        assertNotNull(node.getChild("list"));
    }

    @Test
    void hidesABasicCommandOnceInactive() {
        Home home = new Home();
        registerAll(List.of(home), List.of());
        BasicCommand guarded = (BasicCommand) registrations.get(0)[3];
        assertTrue(guarded.canUse(sender()));
        paper.deactivate();
        assertFalse(guarded.canUse(sender()));
        assertEquals(List.of(), guarded.suggest(source(), new String[0]));
        guarded.execute(source(), new String[0]);
        assertEquals(0, home.executions);
    }

    @Test
    @SuppressWarnings("unchecked")
    void hidesATreeOnceInactive() {
        registerAll(List.of(), List.of(new Warp()));
        LiteralCommandNode<CommandSourceStack> node =
                (LiteralCommandNode<CommandSourceStack>) registrations.get(0)[0];
        assertTrue(node.canUse(source()));
        paper.deactivate();
        assertFalse(node.canUse(source()));
    }

    @Test
    void registersNothingOnceInactive() {
        registerAll(List.of(new Home()), List.of());
        paper.deactivate();
        paper.registerAll(registrar());
        assertEquals(1, registrations.size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void keepsTheTreeOwnRequirement() {
        registerAll(List.of(), List.of(new Restricted()));
        LiteralCommandNode<CommandSourceStack> node =
                (LiteralCommandNode<CommandSourceStack>) registrations.get(0)[0];
        assertFalse(node.canUse(source()));
    }

    private void registerAll(List<BasicCommand> basics, List<CommandTree> trees) {
        List<Object> components = new ArrayList<>(basics);
        components.addAll(trees);
        paper = new PaperCommandRegistrar(
                components.stream()
                        .map(component -> new PaperCommandRegistrar.Declared(component, META.get(component.getClass())))
                        .toList(),
                capturingLogger());
        paper.registerAll(registrar());
    }

    private static PaperCommand command(String value, String description, String... aliases) {
        return new PaperCommand() {
            @Override
            public String value() {
                return value;
            }

            @Override
            public String description() {
                return description;
            }

            @Override
            public String[] aliases() {
                return aliases;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return PaperCommand.class;
            }
        };
    }

    private Commands registrar() {
        return (Commands) proxy(Commands.class, (proxy, method, args) -> {
            if (!method.getName().equals("register") || args.length < 3)
                throw new UnsupportedOperationException(method.getName());
            registrations.add(args);
            Set<String> labels = new HashSet<>();
            labels.add(args[0] instanceof LiteralCommandNode<?> node ? node.getLiteral() : (String) args[0]);
            for (Object alias : (Collection<?>) args[2]) labels.add((String) alias);
            labels.removeAll(taken);
            return labels;
        });
    }

    private static CommandSourceStack source() {
        return (CommandSourceStack) proxy(CommandSourceStack.class, (proxy, method, args) -> {
            throw new UnsupportedOperationException(method.getName());
        });
    }

    private static CommandSender sender() {
        return (CommandSender) proxy(CommandSender.class, (proxy, method, args) -> {
            throw new UnsupportedOperationException(method.getName());
        });
    }

    private static Object proxy(Class<?> type, InvocationHandler handler) {
        return Proxy.newProxyInstance(PaperCommandRegistrarTest.class.getClassLoader(), new Class<?>[] {type}, handler);
    }

    private Logger capturingLogger() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel() == Level.WARNING) warnings.add(record.getMessage());
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        });
        return logger;
    }

    static final class Home implements BasicCommand {

        int executions;

        @Override
        public void execute(CommandSourceStack source, String[] args) {
            executions++;
        }

        @Override
        public Collection<String> suggest(CommandSourceStack source, String[] args) {
            return List.of("spawn");
        }
    }

    static final class NewHome implements BasicCommand {

        @Override
        public void execute(CommandSourceStack source, String[] args) {}
    }

    static final class Warp implements CommandTree {

        @Override
        public LiteralArgumentBuilder<CommandSourceStack> tree(String label) {
            return Commands.literal(label).executes(context -> 1);
        }
    }

    static final class Misrooted implements CommandTree {

        @Override
        public LiteralArgumentBuilder<CommandSourceStack> tree(String label) {
            return Commands.literal("other");
        }
    }

    static final class Broken implements CommandTree {

        @Override
        public LiteralArgumentBuilder<CommandSourceStack> tree(String label) {
            throw new IllegalStateException("broken");
        }
    }

    static final class Cached implements CommandTree {

        final LiteralArgumentBuilder<CommandSourceStack> builder =
                Commands.literal("cached").then(Commands.literal("list").executes(context -> 1));

        @Override
        public LiteralArgumentBuilder<CommandSourceStack> tree(String label) {
            return builder;
        }
    }

    static final class Restricted implements CommandTree {

        @Override
        public LiteralArgumentBuilder<CommandSourceStack> tree(String label) {
            return Commands.literal(label).requires(source -> false);
        }
    }
}
