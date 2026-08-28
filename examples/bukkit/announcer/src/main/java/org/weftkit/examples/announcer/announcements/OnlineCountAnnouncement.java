package org.weftkit.examples.announcer.announcements;

import org.bukkit.plugin.java.JavaPlugin;
import org.weftkit.examples.announcer.broadcast.Announcement;
import org.weftkit.wiring.Singleton;
import org.weftkit.wiring.Wired;

@Wired
@Singleton
final class OnlineCountAnnouncement implements Announcement {

    private final JavaPlugin plugin;

    OnlineCountAnnouncement(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String text() {
        int online = plugin.getServer().getOnlinePlayers().size();
        if (online == 1) return "1 player is weaving right now.";
        return online + " players are weaving right now.";
    }
}
