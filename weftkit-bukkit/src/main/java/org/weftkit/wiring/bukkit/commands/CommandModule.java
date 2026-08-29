package org.weftkit.wiring.bukkit.commands;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;
import org.weftkit.wiring.ModuleLifecycle;
import org.weftkit.wiring.WeftModule;
import org.weftkit.wiring.bukkit.modules.BukkitModules;
import org.weftkit.wiring.runtime.Weft;

/**
 * Binds every wired {@code CommandExecutor} annotated {@code @CommandHandler} to its
 * {@code plugin.yml} command once the graph is up, feature flagged ones excluded, and clears those
 * bindings on deactivation. An executor that is also a {@code TabCompleter} is bound for
 * completion too. Opt out with {@code @Registry(disable = BukkitModules.COMMANDS)}.
 */
@WeftModule(id = BukkitModules.COMMANDS)
public final class CommandModule implements ModuleLifecycle {

    private final JavaPlugin plugin;

    private final Weft weft;

    private final List<PluginCommand> registered = new ArrayList<>();

    public CommandModule(JavaPlugin plugin, Weft weft) {
        this.plugin = plugin;
        this.weft = weft;
    }

    @Override
    public boolean activate() {
        for (CommandExecutor executor : weft.all(CommandExecutor.class)) {
            CommandHandler handler = executor.getClass().getAnnotation(CommandHandler.class);
            if (handler == null) continue;
            PluginCommand command = plugin.getCommand(handler.value());
            if (command == null) {
                plugin.getLogger()
                        .warning("Command '" + handler.value() + "' for "
                                + executor.getClass().getName()
                                + " is not declared in plugin.yml. Left unbound");
                continue;
            }
            command.setExecutor(executor);
            if (executor instanceof TabCompleter completer) command.setTabCompleter(completer);
            registered.add(command);
        }
        return true;
    }

    @Override
    public void deactivate() {
        for (PluginCommand command : registered) {
            command.setExecutor(null);
            command.setTabCompleter(null);
        }
        registered.clear();
    }
}
