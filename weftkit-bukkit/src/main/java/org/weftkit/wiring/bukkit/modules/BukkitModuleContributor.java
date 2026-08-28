package org.weftkit.wiring.bukkit.modules;

import com.google.auto.service.AutoService;
import java.util.List;
import org.weftkit.wiring.bukkit.listeners.ListenerModule;
import org.weftkit.wiring.bukkit.metrics.MetricsModule;
import org.weftkit.wiring.processor.spi.ModuleContributor;

/** Contributes the bundled Bukkit modules to every generated registry, see {@link BukkitModules}. */
@AutoService(ModuleContributor.class)
public final class BukkitModuleContributor implements ModuleContributor {

    @Override
    public List<String> modules() {
        return List.of(ListenerModule.class.getName(), MetricsModule.class.getName());
    }
}
