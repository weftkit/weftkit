package org.weftkit.examples.announcer;

import org.weftkit.examples.announcer.broadcast.AnnouncerModule;
import org.weftkit.wiring.Registry;
import org.weftkit.wiring.bukkit.WeftPlugin;
import org.weftkit.wiring.runtime.registry.ComponentRegistry;

@Registry(modules = AnnouncerModule.class)
public final class AnnouncerPlugin extends WeftPlugin {

    @Override
    protected ComponentRegistry registry() {
        return WeftWiring.INSTANCE;
    }
}
