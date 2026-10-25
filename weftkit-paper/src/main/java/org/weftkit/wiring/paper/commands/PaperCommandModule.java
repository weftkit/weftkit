package org.weftkit.wiring.paper.commands;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.weftkit.wiring.ModuleLifecycle;
import org.weftkit.wiring.WeftModule;
import org.weftkit.wiring.paper.PaperModules;
import org.weftkit.wiring.runtime.Weft;

/**
 * Registers every wired {@code @PaperCommand} component with Paper's command registrar. Activate
 * from {@code onEnable} at the latest, Paper refuses lifecycle handlers afterwards. Deactivation
 * hides the commands, Paper cannot unregister them, and the next command rebuild drops them. Opt out with
 * {@code @Registry(disable = PaperModules.COMMANDS)}.
 */
@WeftModule(id = PaperModules.COMMANDS)
public final class PaperCommandModule implements ModuleLifecycle {

    private final JavaPlugin plugin;

    private final Weft weft;

    private PaperCommandRegistrar registrar;

    public PaperCommandModule(JavaPlugin plugin, Weft weft) {
        this.plugin = plugin;
        this.weft = weft;
    }

    @Override
    public boolean activate() {
        List<PaperCommandRegistrar.Declared> commands = weft.annotated(Object.class, PaperCommand.class).stream()
                .map(command -> new PaperCommandRegistrar.Declared(command.component(), command.annotation()))
                .toList();
        if (commands.isEmpty()) return true;
        PaperCommandRegistrar registrar = new PaperCommandRegistrar(commands, plugin.getLogger());
        try {
            plugin.getLifecycleManager()
                    .registerEventHandler(LifecycleEvents.COMMANDS, event -> registrar.registerAll(event.registrar()));
        } catch (IllegalStateException ex) {
            throw new IllegalStateException(
                    "Paper accepts lifecycle handlers only until the first onEnable returns. Call"
                            + " BukkitWeft.enable from onEnable, and reload or restart the server instead of"
                            + " re-enabling the plugin in place",
                    ex);
        }
        this.registrar = registrar;
        return true;
    }

    @Override
    public void deactivate() {
        if (registrar == null) return;
        registrar.deactivate();
        registrar = null;
        plugin.getServer().getOnlinePlayers().forEach(Player::updateCommands);
    }
}
