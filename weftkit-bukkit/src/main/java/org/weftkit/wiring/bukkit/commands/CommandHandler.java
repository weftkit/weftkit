package org.weftkit.wiring.bukkit.commands;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.weftkit.wiring.HandledBy;

/**
 * Binds a wired {@code CommandExecutor} to the named {@code plugin.yml} command. The bundled
 * command module reads it during enable and sets the executor, so there is no {@code setExecutor}
 * call to write by hand. Make the component a {@code @Singleton}; a {@code @FeatureFlag} binds it
 * only while its flag is on.
 */
@HandledBy(CommandModule.class)
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface CommandHandler {

    /** The command name as declared in {@code plugin.yml}, without the leading slash. */
    String value();
}
