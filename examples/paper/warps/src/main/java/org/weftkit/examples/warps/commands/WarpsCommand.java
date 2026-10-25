package org.weftkit.examples.warps.commands;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.weftkit.examples.warps.storage.WarpStore;
import org.weftkit.wiring.Singleton;
import org.weftkit.wiring.Wired;
import org.weftkit.wiring.paper.commands.PaperCommand;

@Wired
@Singleton
@PaperCommand(value = "warps", description = "List every warp", aliases = "warplist")
public final class WarpsCommand implements BasicCommand {

    private final WarpStore warps;

    public WarpsCommand(WarpStore warps) {
        this.warps = warps;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        Set<String> names = warps.names();
        if (names.isEmpty()) {
            source.getSender().sendMessage(Component.text("No warps yet.", NamedTextColor.GRAY));
            return;
        }
        source.getSender().sendMessage(Component.text("Warps: " + String.join(", ", names), NamedTextColor.YELLOW));
    }
}
