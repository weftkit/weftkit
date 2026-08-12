package org.weftkit.wiring.bukkit;

import org.bukkit.plugin.java.JavaPlugin;
import org.weftkit.wiring.runtime.ComponentRegistry;
import org.weftkit.wiring.runtime.WeftLoader;

/**
 * Base class for plugins that hand their lifecycle to weftkit. {@link #onEnable()} loads the
 * {@link #registry()} through {@link BukkitWeft} and calls {@link #onWeftEnable} once the graph
 * is up, {@link #onDisable()} runs {@link #onWeftDisable} and tears the graph down. When a
 * component aborts startup, weftkit disables the plugin and neither hook runs. Plugins that need
 * full control over enable and disable use {@link BukkitWeft} directly instead.
 */
public abstract class WeftPlugin extends JavaPlugin {

    private WeftLoader loader;

    /** Returns the generated registry, {@code WeftWiring.INSTANCE} in the plugin main package. */
    protected abstract ComponentRegistry registry();

    /** Extra ambient values offered to every component, in addition to the plugin itself. */
    protected Object[] ambientValues() {
        return new Object[0];
    }

    /** Runs after the graph loaded successfully, with listeners already registered. */
    protected void onWeftEnable(WeftLoader loader) {}

    /** Runs before the graph is torn down, with every component still resolvable. */
    protected void onWeftDisable(WeftLoader loader) {}

    /** Returns the loader while the plugin is enabled, or null outside that window. */
    protected final WeftLoader loader() {
        return loader;
    }

    @Override
    public final void onEnable() {
        loader = BukkitWeft.enable(this, registry(), ambientValues());
        if (loader == null) return;
        onWeftEnable(loader);
    }

    @Override
    public final void onDisable() {
        if (loader != null) onWeftDisable(loader);
        BukkitWeft.disable(this, loader);
        loader = null;
    }
}
