package org.weftkit.wiring;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a weftkit module, woven into a consuming plugin's graph at compile time.
 *
 * <p>A module is an eager {@link Singleton}: it takes collaborators through its constructor, and
 * its load position follows from what it injects. Implement {@link ModuleLifecycle} for hooks
 * that run once the whole graph is up. Modules join a plugin via {@code @Registry(modules = ...)}
 * or an adapter's {@code ModuleContributor} service, are generated as hard references (shading
 * and minimization safe), and are removed entirely by {@code @Registry(disable = ...)}.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface WeftModule {

    /**
     * Stable id for {@code @Registry(disable = ...)} and duplicate detection, namespaced like
     * {@code "bukkit:listeners"}. Define it in an id holder next to the module (e.g.
     * {@code BukkitModules}), so an opt-out never imports the module class it is switching off.
     */
    String id();

    /**
     * Components shipped with this module, each a public {@code @Wired} class. Their wiring
     * annotations apply as usual. Disabling the module drops them too.
     */
    Class<?>[] components() default {};
}
