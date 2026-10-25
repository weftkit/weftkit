package org.weftkit.wiring.paper.processor;

import com.google.auto.service.AutoService;
import java.util.List;
import org.weftkit.wiring.processor.spi.ModuleContributor;

/**
 * Contributes the bundled Paper modules to every generated registry, see {@code PaperModules}.
 * The Bukkit modules and ambient types come from the Bukkit processor on the same path.
 */
@AutoService(ModuleContributor.class)
public final class PaperModuleContributor implements ModuleContributor {

    @Override
    public List<String> modules() {
        return List.of("org.weftkit.wiring.paper.commands.PaperCommandModule");
    }
}
