package org.weftkit.examples.homes.storage;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.weftkit.wiring.Loader;
import org.weftkit.wiring.Provides;
import org.weftkit.wiring.Singleton;
import org.weftkit.wiring.Wired;

@Wired
@Singleton
final class HomesFile implements Loader {

    private final JavaPlugin plugin;

    private final File file;

    private YamlConfiguration homes;

    HomesFile(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "homes.yml");
    }

    @Override
    public boolean load() {
        YamlConfiguration loaded = new YamlConfiguration();
        try {
            if (file.exists()) loaded.load(file);
        } catch (IOException | InvalidConfigurationException ex) {
            plugin.getLogger().log(Level.SEVERE, "homes.yml is unreadable, refusing to start", ex);
            return false;
        }
        homes = loaded;
        return true;
    }

    @Override
    public void unload() {
        try {
            homes.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save homes.yml", ex);
        }
    }

    @Provides
    YamlConfiguration homes() {
        return homes;
    }
}
