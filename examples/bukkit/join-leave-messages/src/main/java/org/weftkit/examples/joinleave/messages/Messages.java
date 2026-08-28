package org.weftkit.examples.joinleave.messages;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.weftkit.wiring.Singleton;
import org.weftkit.wiring.Wired;

@Wired
@Singleton
public final class Messages {

    public String joined(Player player) {
        return ChatColor.YELLOW + player.getName() + " wove into the server.";
    }

    public String left(Player player) {
        return ChatColor.YELLOW + player.getName() + " unraveled from the server.";
    }
}
