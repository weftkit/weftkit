package org.weftkit.wiring.paper.commands;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.weftkit.wiring.HandledBy;

/**
 * Registers a wired {@code BasicCommand} or {@link CommandTree} with Paper's command registrar
 * through the bundled command module. Needs no {@code plugin.yml} entry, so it also works for
 * {@code paper-plugin.yml} plugins. The component must be a {@code @Singleton}; a
 * {@code @FeatureFlag} registers it only while its flag is on.
 */
@HandledBy(PaperCommandModule.class)
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface PaperCommand {

    /** The lowercase command label, without the leading slash. */
    String value();

    /** The help description, none when blank. */
    String description() default "";

    /** Alternative labels, distinct from each other and from {@link #value()}. */
    String[] aliases() default {};
}
