package org.weftkit.wiring.paper.commands;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Collection;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.bukkit.command.CommandSender;

/** Delegates to a {@code BasicCommand} only while the module is active, hidden otherwise. */
final class GuardedBasicCommand implements BasicCommand {

    private final BasicCommand delegate;

    private final BooleanSupplier active;

    GuardedBasicCommand(BasicCommand delegate, BooleanSupplier active) {
        this.delegate = delegate;
        this.active = active;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        if (active.getAsBoolean()) delegate.execute(source, args);
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        return active.getAsBoolean() ? delegate.suggest(source, args) : List.of();
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return active.getAsBoolean() && delegate.canUse(sender);
    }

    @Override
    public String permission() {
        return delegate.permission();
    }
}
