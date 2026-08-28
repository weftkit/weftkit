package org.weftkit.examples.announcer.broadcast;

import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.weftkit.wiring.ModuleLifecycle;
import org.weftkit.wiring.WeftModule;
import org.weftkit.wiring.runtime.Weft;

/** Broadcasts the wired {@link Announcement} components in rotation while active. */
@WeftModule(id = AnnouncerModules.BROADCASTS)
public final class AnnouncerModule implements ModuleLifecycle {

    private final JavaPlugin plugin;

    private final Weft weft;

    private final AnnouncerSettings settings;

    private BukkitTask task;

    private int next;

    public AnnouncerModule(JavaPlugin plugin, Weft weft, AnnouncerSettings settings) {
        this.plugin = plugin;
        this.weft = weft;
        this.settings = settings;
    }

    @Override
    public boolean activate() {
        List<Announcement> rotation = weft.all(Announcement.class);
        if (rotation.isEmpty()) return true;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin,
                () -> broadcast(rotation), settings.intervalTicks(), settings.intervalTicks());
        return true;
    }

    @Override
    public void deactivate() {
        if (task == null) return;
        task.cancel();
        task = null;
    }

    private void broadcast(List<Announcement> rotation) {
        Announcement announcement = rotation.get(next);
        next = (next + 1) % rotation.size();
        plugin.getServer().broadcastMessage(ChatColor.GOLD + announcement.text());
    }
}
