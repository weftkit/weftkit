package org.weftkit.wiring.paper.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;

/** A {@code @PaperCommand} component declared as a full Brigadier tree. */
public interface CommandTree {

    /** Returns the tree rooted at {@code label}, the {@code @PaperCommand} value. */
    LiteralArgumentBuilder<CommandSourceStack> tree(String label);
}
