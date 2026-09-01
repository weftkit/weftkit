package org.weftkit.examples.joinleave;

import org.weftkit.wiring.Registry;
import org.weftkit.wiring.bukkit.WeftPlugin;
import org.weftkit.wiring.registry.ComponentRegistry;

@Registry
public final class JoinLeavePlugin extends WeftPlugin {

    @Override
    protected ComponentRegistry registry() {
        return WeftWiring.INSTANCE;
    }
}
