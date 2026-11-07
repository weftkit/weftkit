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
import org.weftkit.wiring.bukkit.scheduler.TaskScope;

@Wired
@Singleton
final class HomesFile implements Loader {

    private static final long AUTOSAVE_TICKS = 20L * 60 * 5;

    private final JavaPlugin plugin;

    private final TaskScope tasks;

    private final File file;

    private YamlConfiguration homes;

    HomesFile(JavaPlugin plugin, TaskScope tasks) {
        this.plugin = plugin;
        this.tasks = tasks;
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
        tasks.global().runRepeating(this::save, AUTOSAVE_TICKS, AUTOSAVE_TICKS);
        return true;
    }

    @Override
    public void unload() {
        save();
    }

    @Provides
    YamlConfiguration homes() {
        return homes;
    }

    private void save() {
        try {
            homes.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save homes.yml", ex);
        }
    }
}
