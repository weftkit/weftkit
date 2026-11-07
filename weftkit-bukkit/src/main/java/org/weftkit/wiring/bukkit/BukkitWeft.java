package org.weftkit.wiring.bukkit;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Level;
import org.bukkit.command.CommandExecutor;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.weftkit.wiring.bukkit.scheduler.Scheduler;
import org.weftkit.wiring.bukkit.scheduler.SchedulerModule;
import org.weftkit.wiring.bukkit.scheduler.Schedulers;
import org.weftkit.wiring.registry.ComponentRegistry;
import org.weftkit.wiring.runtime.Modules;
import org.weftkit.wiring.runtime.WeftLoader;

/**
 * The Bukkit entry points for weftkit: {@link #enable} from {@code onEnable} and {@link #disable}
 * from {@code onDisable}.
 */
public final class BukkitWeft {

    private BukkitWeft() {}

    /** Enables with a null scheduler, see {@link #enable(JavaPlugin, ComponentRegistry, Scheduler, Object...)}. */
    public static WeftLoader enable(JavaPlugin plugin, ComponentRegistry registry, Object... extraAmbient) {
        return enable(plugin, registry, null, extraAmbient);
    }

    /**
     * Loads the registry with the plugin, the scheduler, and the extra values as ambient roots,
     * then activates its modules. A null scheduler uses a {@code @Provides Scheduler} product or
     * a wired implementation, else the Bukkit scheduler. On any failure the plugin is disabled
     * and null is returned.
     */
    public static WeftLoader enable(
            JavaPlugin plugin, ComponentRegistry registry, Scheduler scheduler, Object... extraAmbient) {
        return enableWith(plugin, registry, () -> scheduler, extraAmbient);
    }

    /** Enables with a scheduler the supplier yields inside the startup failure handling. */
    static WeftLoader enableWith(
            JavaPlugin plugin, ComponentRegistry registry, Supplier<Scheduler> scheduler, Object[] extraAmbient) {
        Object[] extra = extraAmbient == null ? new Object[0] : extraAmbient;
        WeftLoader loader = null;
        try {
            Scheduler offered = ambientScheduler(plugin, registry, scheduler.get(), extra);
            loader = new WeftLoader(registry, ambient(plugin, offered, extra));
            if (!loader.load()) return abort(plugin, loader);
            warnMissingModules(plugin, loader);
            if (!Modules.activate(loader)) return abort(plugin, loader);
        } catch (RuntimeException | Error ex) {
            plugin.getLogger().log(Level.SEVERE, "Error during weftkit startup", ex);
            // A load failure already unloaded, an activation failure has not; unload is idempotent
            try {
                abort(plugin, loader);
            } catch (RuntimeException | Error teardown) {
                if (ex != teardown) ex.addSuppressed(teardown);
            }
            if (ex instanceof Error error) throw error;
            return null;
        }
        return loader;
    }

    /** Deactivates the modules and unloads the graph, which cancels the scheduler's tasks. Accepts null. */
    public static void disable(JavaPlugin plugin, WeftLoader loader) {
        if (loader == null) return;
        Teardown teardown = new Teardown();
        teardown.run(() -> Modules.deactivate(loader));
        teardown.run(loader::unload);
        teardown.report(plugin, "Error during weftkit shutdown");
    }

    /** The scheduler the loader runs with: the ambient value, else the loaded {@link SchedulerModule}'s, else null. */
    public static Scheduler scheduler(WeftLoader loader) {
        Scheduler ambient = loader.ambient(Scheduler.class);
        if (ambient != null) return ambient;
        SchedulerModule module = loader.get(SchedulerModule.class);
        return module == null ? null : module.scheduler();
    }

    private static Scheduler ambientScheduler(
            JavaPlugin plugin, ComponentRegistry registry, Scheduler explicit, Object[] extra) {
        List<Class<?>> declared = registry.ambientTypes();
        boolean open = declared.isEmpty() || declared.contains(Scheduler.class);
        if (explicit != null) {
            if (!open)
                throw new IllegalArgumentException("The registry declares ambient types without Scheduler, so the"
                        + " scheduler passed to BukkitWeft.enable cannot reach its components. Compile the registry"
                        + " with weftkit-bukkit-processor, or drop the scheduler");
            return explicit;
        }
        if (!open || provided(registry)) return null;
        for (Object value : extra) if (value instanceof Scheduler) return null;
        if (Schedulers.isFolia())
            throw new IllegalStateException("Folia detected. Add weftkit-folia to the plugin's dependencies, or pass"
                    + " a Scheduler to BukkitWeft.enable");
        return Schedulers.bukkit(plugin);
    }

    private static boolean provided(ComponentRegistry registry) {
        return registry.productOwners().getOrDefault(Scheduler.class, Map.of()).containsKey("")
                || registry.bindings().getOrDefault(Scheduler.class, Map.of()).containsKey("");
    }

    private static void warnMissingModules(JavaPlugin plugin, WeftLoader loader) {
        List<Class<? extends Annotation>> unhandled = Modules.unhandled(loader);
        if (loader.registry().modules().isEmpty()) {
            if (!unhandled.isEmpty() || hasListenersOrCommands(loader.registry()))
                plugin.getLogger()
                        .warning("The registry contains no weftkit modules, so the wired listeners and commands will"
                                + " not be registered. Is weftkit-bukkit-processor missing from the"
                                + " annotationProcessor path?");
            return;
        }
        for (Class<? extends Annotation> annotation : unhandled)
            plugin.getLogger()
                    .warning("The registry lacks the module handling @" + annotation.getSimpleName()
                            + ", so those components will not be registered. Is its processor missing from"
                            + " the annotationProcessor path?");
    }

    private static boolean hasListenersOrCommands(ComponentRegistry registry) {
        return registry.parameters().keySet().stream()
                .anyMatch(
                        type -> Listener.class.isAssignableFrom(type) || CommandExecutor.class.isAssignableFrom(type));
    }

    private static WeftLoader abort(JavaPlugin plugin, WeftLoader loader) {
        try {
            if (loader != null) loader.unload();
        } catch (RuntimeException | Error ex) {
            plugin.getLogger().log(Level.SEVERE, "Error during weftkit teardown", ex);
            if (ex instanceof Error error) throw error;
        } finally {
            plugin.getServer().getPluginManager().disablePlugin(plugin);
        }
        return null;
    }

    private static Object[] ambient(JavaPlugin plugin, Scheduler scheduler, Object[] extra) {
        Object[] ambient = new Object[extra.length + 2];
        ambient[0] = plugin;
        ambient[1] = scheduler;
        System.arraycopy(extra, 0, ambient, 2, extra.length);
        return ambient;
    }

    private static final class Teardown {

        private Throwable failure;

        void run(Runnable step) {
            try {
                step.run();
            } catch (RuntimeException | Error ex) {
                if (failure == null) failure = ex;
                else if (failure != ex) failure.addSuppressed(ex);
            }
        }

        void report(JavaPlugin plugin, String message) {
            if (failure != null) plugin.getLogger().log(Level.SEVERE, message, failure);
            if (failure instanceof Error error) throw error;
        }
    }
}
