package org.weftkit.wiring.paper.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Registers declared commands with Paper, each guarded to work only while active. A command
 * claiming a label of an earlier one is left unregistered, Paper would let it take over. Paper
 * keeps the lifecycle handler for good, so deactivation drops the commands and later rebuilds
 * register none.
 */
final class PaperCommandRegistrar {

    private final Logger logger;

    private volatile List<Declared> commands;

    PaperCommandRegistrar(List<Declared> commands, Logger logger) {
        this.commands = List.copyOf(commands);
        this.logger = logger;
    }

    void deactivate() {
        commands = List.of();
    }

    boolean active() {
        return !commands.isEmpty();
    }

    /** A {@code BasicCommand} or {@link CommandTree} with its {@code @PaperCommand}. */
    record Declared(Object command, PaperCommand meta) {

        String label() {
            return meta.value();
        }

        String description() {
            return meta.description().isBlank() ? null : meta.description();
        }

        List<String> aliases() {
            return List.of(meta.aliases());
        }

        List<String> labels() {
            List<String> labels = new ArrayList<>(List.of(label()));
            labels.addAll(aliases());
            return labels;
        }

        String owner() {
            return command.getClass().getName();
        }
    }

    void registerAll(Commands registrar) {
        Map<String, Declared> claimed = new HashMap<>();
        for (Declared command : commands) {
            List<String> clashing =
                    command.labels().stream().filter(claimed::containsKey).toList();
            if (!clashing.isEmpty()) {
                logger.warning("Labels " + clashing + " of " + command.owner() + " are already claimed by "
                        + claimed.get(clashing.get(0)).owner() + ", both enabled. Left unregistered");
                continue;
            }
            try {
                Set<String> registered =
                        switch (command.command()) {
                            case BasicCommand basic ->
                                registrar.register(
                                        command.label(),
                                        command.description(),
                                        command.aliases(),
                                        new GuardedBasicCommand(basic, this::active));
                            case CommandTree tree -> registerTree(registrar, tree, command);
                            default -> throw new IllegalStateException("Neither a BasicCommand nor a CommandTree");
                        };
                if (registered == null) continue;
                command.labels().forEach(label -> claimed.put(label, command));
                warnShadowed(command, registered);
            } catch (RuntimeException ex) {
                logger.log(
                        Level.SEVERE,
                        "Command '" + command.label() + "' of " + command.owner() + " failed to register",
                        ex);
            }
        }
    }

    private Set<String> registerTree(Commands registrar, CommandTree tree, Declared command) {
        LiteralCommandNode<CommandSourceStack> root = tree.tree(command.label()).build();
        if (!root.getLiteral().equals(command.label())) {
            logger.warning("Command tree of " + command.owner() + " is rooted at '" + root.getLiteral()
                    + "' instead of '" + command.label() + "'. Left unregistered");
            return null;
        }
        return registrar.register(guard(root), command.description(), command.aliases());
    }

    private LiteralCommandNode<CommandSourceStack> guard(LiteralCommandNode<CommandSourceStack> root) {
        Predicate<CommandSourceStack> requirement = root.getRequirement();
        LiteralArgumentBuilder<CommandSourceStack> copy =
                root.createBuilder().requires(source -> active() && requirement.test(source));
        if (root.getRedirect() == null) root.getChildren().forEach(copy::then);
        return copy.build();
    }

    private void warnShadowed(Declared command, Set<String> registered) {
        List<String> shadowed = new ArrayList<>();
        if (!registered.contains(command.label())) shadowed.add(command.label());
        for (String alias : command.aliases()) if (!registered.contains(alias)) shadowed.add(alias);
        if (!shadowed.isEmpty())
            logger.warning("Labels " + shadowed + " of " + command.owner()
                    + " are taken by other commands, reachable only through the plugin namespace");
    }
}
