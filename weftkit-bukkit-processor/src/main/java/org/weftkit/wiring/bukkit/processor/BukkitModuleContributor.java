package org.weftkit.wiring.bukkit.processor;

import com.google.auto.service.AutoService;
import java.util.List;
import org.weftkit.wiring.bukkit.commands.CommandModule;
import org.weftkit.wiring.bukkit.listeners.ListenerModule;
import org.weftkit.wiring.bukkit.metrics.MetricsModule;
import org.weftkit.wiring.processor.spi.ModuleContributor;

/** Contributes the bundled Bukkit modules to every generated registry, see {@link BukkitModules}. */
@AutoService(ModuleContributor.class)
public final class BukkitModuleContributor implements ModuleContributor {

    @Override
    public List<String> modules() {
        return List.of(ListenerModule.class.getName(), CommandModule.class.getName(), MetricsModule.class.getName());
    }

    // Named as a string. The Bukkit API is not on the consumer's processor path, so a class
    // literal would fail to load
    @Override
    public List<String> ambientTypes() {
        return List.of("org.bukkit.plugin.java.JavaPlugin");
    }
}
