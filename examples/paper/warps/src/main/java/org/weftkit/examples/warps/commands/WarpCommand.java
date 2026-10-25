package org.weftkit.examples.warps.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.weftkit.examples.warps.storage.WarpStore;
import org.weftkit.wiring.Singleton;
import org.weftkit.wiring.Wired;
import org.weftkit.wiring.paper.commands.CommandTree;
import org.weftkit.wiring.paper.commands.PaperCommand;

@Wired
@Singleton
@PaperCommand(value = "warp", description = "Teleport to or set a named warp")
public final class WarpCommand implements CommandTree {

    private final WarpStore warps;

    public WarpCommand(WarpStore warps) {
        this.warps = warps;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> tree(String label) {
        return Commands.literal(label)
                .requires(source -> source.getExecutor() instanceof Player)
                .then(Commands.literal("set")
                        .requires(source -> source.getSender().hasPermission("warps.set"))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(this::set)))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            warps.names().forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(this::teleport));
    }

    private int set(CommandContext<CommandSourceStack> context) {
        Player player = (Player) context.getSource().getExecutor();
        String name = StringArgumentType.getString(context, "name");
        if (name.equals("set")) {
            player.sendMessage(Component.text("A warp cannot be named set.", NamedTextColor.RED));
            return 0;
        }
        warps.set(name, player.getLocation());
        player.sendMessage(Component.text("Warp " + name + " set.", NamedTextColor.YELLOW));
        return Command.SINGLE_SUCCESS;
    }

    private int teleport(CommandContext<CommandSourceStack> context) {
        Player player = (Player) context.getSource().getExecutor();
        String name = StringArgumentType.getString(context, "name");
        Location warp = warps.find(name);
        if (warp == null) {
            player.sendMessage(Component.text("No warp named " + name + ".", NamedTextColor.RED));
            return 0;
        }
        player.teleport(warp);
        return Command.SINGLE_SUCCESS;
    }
}
