package org.weftkit.examples.homes;

import org.weftkit.wiring.Registry;
import org.weftkit.wiring.bukkit.WeftPlugin;
import org.weftkit.wiring.runtime.registry.ComponentRegistry;

@Registry
public final class HomesPlugin extends WeftPlugin {

    @Override
    protected ComponentRegistry registry() {
        return WeftWiring.INSTANCE;
    }
}
