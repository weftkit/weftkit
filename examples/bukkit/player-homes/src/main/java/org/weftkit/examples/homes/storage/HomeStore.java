package org.weftkit.examples.homes.storage;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.weftkit.wiring.Singleton;
import org.weftkit.wiring.Wired;

@Wired
@Singleton
public final class HomeStore {

    private final YamlConfiguration homes;

    public HomeStore(YamlConfiguration homes) {
        this.homes = homes;
    }

    public void set(Player player) {
        homes.set(player.getUniqueId().toString(), player.getLocation());
    }

    public Location find(Player player) {
        return homes.getLocation(player.getUniqueId().toString());
    }
}
