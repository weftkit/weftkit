package org.weftkit.examples.homes.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.weftkit.examples.homes.storage.HomeStore;
import org.weftkit.wiring.Singleton;
import org.weftkit.wiring.Wired;

@Wired
@Singleton
public final class SetHomeCommand implements CommandExecutor {

    private final HomeStore homes;

    public SetHomeCommand(HomeStore homes) {
        this.homes = homes;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players have homes.");
            return true;
        }
        homes.set(player);
        player.sendMessage(ChatColor.YELLOW + "Home set.");
        return true;
    }
}
