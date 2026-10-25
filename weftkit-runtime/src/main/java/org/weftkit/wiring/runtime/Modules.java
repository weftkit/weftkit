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
 * {@link WeftLoader#unload()}. Activation runs in load order, deactivation in reverse, on the
 * graph's singleton instances.
 */
public final class Modules {

    private Modules() {}

    /**
     * Activates every module. When one returns false or throws, the already activated modules are
     * deactivated in reverse before returning false or rethrowing (teardown failures suppressed).
     * The graph stays loaded either way.
     */
    public static boolean activate(WeftLoader loader) {
        List<Class<?>> modules = loader.registry().modules();
        for (int index = 0; index < modules.size(); index++) {
            ModuleLifecycle module = lifecycle(loader, modules.get(index));
            if (module == null) continue;
            boolean activated;
            try {
                activated = module.activate();
            } catch (RuntimeException ex) {
                try {
                    deactivate(loader, index);
                } catch (RuntimeException teardown) {
                    ex.addSuppressed(teardown);
                }
                throw ex;
            }
            if (!activated) {
                deactivate(loader, index);
                return false;
            }
        }
        return true;
    }

    /**
     * Deactivates every module in reverse order, continuing past failures and rethrowing the first
     * with the rest suppressed. Must run before the graph unloads.
     */
    public static void deactivate(WeftLoader loader) {
        deactivate(loader, loader.registry().modules().size());
    }

    private static void deactivate(WeftLoader loader, int count) {
        List<Class<?>> modules = loader.registry().modules();
        RuntimeException failure = null;
        for (int index = count - 1; index >= 0; index--) {
            ModuleLifecycle module = lifecycle(loader, modules.get(index));
            if (module == null) continue;
            try {
                module.deactivate();
            } catch (RuntimeException ex) {
                if (failure == null) failure = ex;
                else failure.addSuppressed(ex);
            }
        }
        if (failure != null) throw failure;
    }

    /**
     * The handled annotations carried by an enabled component whose {@link HandledBy} module is
     * missing from the registry, sorted by name. Those components stay inert. Owner thread only.
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

    private static ModuleLifecycle lifecycle(WeftLoader loader, Class<?> type) {
        return loader.get(type) instanceof ModuleLifecycle module ? module : null;
    }
}
