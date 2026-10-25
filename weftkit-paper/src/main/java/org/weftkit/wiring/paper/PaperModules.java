package org.weftkit.wiring.paper;

import org.weftkit.wiring.paper.commands.PaperCommandModule;

/**
 * The ids of the modules bundled with the Paper adapter, on top of {@code BukkitModules}. Use the
 * constants in {@code @Registry(disable = ...)}.
 */
public final class PaperModules {

    /** The Brigadier command registration of {@link PaperCommandModule}. */
    public static final String COMMANDS = "paper:commands";

    private PaperModules() {}
}
