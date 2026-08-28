package org.weftkit.examples.homes.commands;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.weftkit.examples.homes.storage.HomeStore;
import org.weftkit.wiring.Singleton;
import org.weftkit.wiring.Wired;

@Wired
@Singleton
public final class HomeCommand implements CommandExecutor {

    private final HomeStore homes;

    public HomeCommand(HomeStore homes) {
        this.homes = homes;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players have homes.");
            return true;
        }
        Location home = homes.find(player);
        if (home == null) {
            player.sendMessage(ChatColor.RED + "No home set, use /sethome first.");
            return true;
        }
        player.teleport(home);
        player.sendMessage(ChatColor.YELLOW + "Welcome home.");
        return true;
    }
}
