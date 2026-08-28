package org.weftkit.examples.homes;

import org.weftkit.examples.homes.commands.HomeCommand;
import org.weftkit.examples.homes.commands.SetHomeCommand;
import org.weftkit.wiring.Registry;
import org.weftkit.wiring.bukkit.WeftPlugin;
import org.weftkit.wiring.runtime.registry.ComponentRegistry;
import org.weftkit.wiring.runtime.WeftLoader;

@Registry
public final class HomesPlugin extends WeftPlugin {

    @Override
    protected ComponentRegistry registry() {
        return WeftWiring.INSTANCE;
    }

    @Override
    protected void onWeftEnable(WeftLoader loader) {
        getCommand("sethome").setExecutor(loader.get(SetHomeCommand.class));
        getCommand("home").setExecutor(loader.get(HomeCommand.class));
    }
}
