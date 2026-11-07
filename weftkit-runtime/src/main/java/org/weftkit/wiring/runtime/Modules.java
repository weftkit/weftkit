package org.weftkit.wiring.runtime;

import java.lang.annotation.Annotation;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.weftkit.wiring.HandledBy;
import org.weftkit.wiring.ModuleLifecycle;
import org.weftkit.wiring.WeftModule;
import org.weftkit.wiring.registry.ComponentRegistry;

/**
 * Drives the {@link ModuleLifecycle} hooks of a loaded graph's {@link WeftModule}s. Adapters call
 * {@link #activate} after {@link WeftLoader#load()} and {@link #deactivate} before
 * {@link WeftLoader#unload()}, which also deactivates any still-active modules. Activation runs
 * in load order, deactivation in reverse, on the
 * graph's singleton instances.
 */
public final class Modules {

    private Modules() {}

    /**
     * Activates every module. When one returns false or throws, the attempted modules are
     * deactivated in reverse before returning false or rethrowing (teardown failures suppressed).
     * The graph stays loaded either way. Repeated activation while active is a no-op; after
     * deactivation or a failed attempt, unload and load before retrying.
     */
    public static boolean activate(WeftLoader loader) {
        return loader.activateModules();
    }

    /** Deactivates the attempted modules once, in reverse order. Also called by graph unload. */
    public static void deactivate(WeftLoader loader) {
        loader.deactivateModules();
    }

    /**
     * The handled annotations carried by an enabled component whose {@link HandledBy} module is
     * missing from the registry, sorted by name. Those components stay inert. Owner thread only
     * until the graph is loaded.
     */
    public static List<Class<? extends Annotation>> unhandled(WeftLoader loader) {
        ComponentRegistry registry = loader.registry();
        Set<String> modules = registry.modules().stream().map(Class::getName).collect(Collectors.toSet());
        Set<Class<? extends Annotation>> missing = registry.handlers().entrySet().stream()
                .filter(handler -> !modules.contains(handler.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        if (missing.isEmpty()) return List.of();
        return loader.enabledAnnotations()
                .<Class<? extends Annotation>>map(Annotation::annotationType)
                .filter(missing::contains)
                .distinct()
                .sorted(Comparator.comparing(Class::getName))
                .toList();
    }
}
