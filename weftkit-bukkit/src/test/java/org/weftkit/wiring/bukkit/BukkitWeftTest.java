package org.weftkit.wiring.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.weftkit.wiring.registry.TestRegistry.on;

import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.JavaPluginLoader;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.weftkit.wiring.Loader;
import org.weftkit.wiring.ModuleLifecycle;
import org.weftkit.wiring.bukkit.scheduler.Async;
import org.weftkit.wiring.bukkit.scheduler.Region;
import org.weftkit.wiring.bukkit.scheduler.Scheduler;
import org.weftkit.wiring.bukkit.scheduler.SchedulerModule;
import org.weftkit.wiring.registry.TestRegistry;
import org.weftkit.wiring.runtime.WeftLoader;
import org.weftkit.wiring.runtime.exception.ResolutionException;

class BukkitWeftTest {

    private final List<String> events = new ArrayList<>();

    private final List<String> disabled = new ArrayList<>();

    private final TestPlugin plugin = new TestPlugin(server());

    private final RecordingScheduler scheduler = new RecordingScheduler();

    @Test
    void disableCancelsThroughTheSchedulerModuleAfterTheOtherUnloadHooks() {
        WeftLoader loader = BukkitWeft.enable(plugin, managed(true), scheduler);
        assertNotNull(loader);
        BukkitWeft.disable(plugin, loader);
        assertEquals(List.of("unload", "cancelAll"), events);
        assertTrue(disabled.isEmpty());
    }

    @Test
    void anAbortedEnableCancelsTasksAndDisablesThePlugin() {
        assertNull(BukkitWeft.enable(plugin, managed(false), scheduler));
        assertEquals(List.of("unload", "cancelAll"), events);
        assertEquals(List.of("Test"), disabled);
    }

    @Test
    void disableWithoutALoaderIsANoOp() {
        BukkitWeft.disable(plugin, null);
        assertTrue(events.isEmpty());
    }

    @Test
    void offersAnExplicitSchedulerToARegistryDeclaringIt() {
        WeftLoader loader =
                BukkitWeft.enable(plugin, registry(true).ambient(TestPlugin.class, Scheduler.class), scheduler);
        assertSame(scheduler, loader.ambient(Scheduler.class));
        assertSame(scheduler, BukkitWeft.scheduler(loader));
        assertSame(plugin, loader.ambient(TestPlugin.class));
    }

    @Test
    void defaultsToTheBukkitScheduler() {
        WeftLoader loader = BukkitWeft.enable(plugin, registry(true).ambient(TestPlugin.class, Scheduler.class));
        assertNotNull(loader.ambient(Scheduler.class));
        assertSame(loader.ambient(Scheduler.class), BukkitWeft.scheduler(loader));
        BukkitWeft.disable(plugin, loader);
        assertEquals(List.of("unload"), events);
    }

    @Test
    void usesTheProvidedSchedulerWhenAnEagerSingletonProvidesOne() {
        WeftLoader loader = BukkitWeft.enable(plugin, providedRegistry(true));
        assertNull(loader.ambient(Scheduler.class));
        assertSame(scheduler, BukkitWeft.scheduler(loader));
        BukkitWeft.disable(plugin, loader);
        assertEquals(List.of("unload", "cancelAll"), events);
    }

    @Test
    void usesAWiredSchedulerImplementationWhenOneIsBound() {
        TestRegistry registry = new TestRegistry()
                .ambient(TestPlugin.class, Scheduler.class)
                .eager(RecordingScheduler.class, args -> scheduler)
                .eager(SchedulerModule.class, args -> new SchedulerModule((Scheduler) args[0]), on(Scheduler.class))
                .eager(Recorder.class, args -> new Recorder(true))
                .bindings(() -> Map.of(Scheduler.class, Map.of("", RecordingScheduler.class)));
        WeftLoader loader = BukkitWeft.enable(plugin, registry);
        assertNull(loader.ambient(Scheduler.class));
        assertSame(scheduler, BukkitWeft.scheduler(loader));
        BukkitWeft.disable(plugin, loader);
        assertEquals(List.of("unload", "cancelAll"), events);
    }

    @Test
    void anExplicitSchedulerOverridesTheProvidedOne() {
        Scheduler explicit = new RecordingScheduler();
        WeftLoader loader = BukkitWeft.enable(plugin, providedRegistry(true), explicit);
        assertSame(explicit, BukkitWeft.scheduler(loader));
    }

    @Test
    void aSchedulerAmongTheExtraValuesIsNotDoubled() {
        WeftLoader loader = BukkitWeft.enable(
                plugin, registry(true).ambient(TestPlugin.class, Scheduler.class), new Object[] {scheduler});
        assertSame(scheduler, loader.ambient(Scheduler.class));
    }

    @Test
    void aNullExtraArrayMeansNoExtraValues() {
        WeftLoader loader = BukkitWeft.enable(plugin, registry(true), (Object[]) null);
        assertNotNull(loader);
        assertSame(plugin, loader.ambient(TestPlugin.class));
    }

    @Test
    void rejectsASchedulerForARegistryDeclaringAmbientTypesWithoutIt() {
        assertNull(BukkitWeft.enable(plugin, registry(true).ambient(TestPlugin.class), scheduler));
        assertTrue(events.isEmpty());
        assertEquals(List.of("Test"), disabled);
    }

    @Test
    void aRegistryDeclaringAmbientTypesWithoutTheSchedulerGetsNone() {
        WeftLoader loader = BukkitWeft.enable(plugin, registry(true).ambient(TestPlugin.class));
        assertNotNull(loader);
        assertNull(BukkitWeft.scheduler(loader));
        BukkitWeft.disable(plugin, loader);
        assertEquals(List.of("unload"), events);
    }

    @Test
    void anAmbiguousSchedulerFailsTheLookupAndNothingElse() {
        Scheduler other = (Scheduler) proxy(Scheduler.class, (proxy, method, args) -> null);
        TestRegistry registry =
                registry(true).eager(TestModule.class, args -> new TestModule()).module(TestModule.class);
        WeftLoader loader = BukkitWeft.enable(plugin, registry, scheduler, other);
        assertNotNull(loader);
        assertThrows(ResolutionException.class, () -> BukkitWeft.scheduler(loader));
        BukkitWeft.disable(plugin, loader);
        assertEquals(List.of("activate", "deactivate", "unload"), events);
    }

    @Test
    void theSchedulerModuleCancelsAProvidedSchedulerWhenTheLoadAborts() {
        assertNull(BukkitWeft.enable(plugin, providedRegistry(false)));
        assertEquals(List.of("unload", "cancelAll"), events);
        assertEquals(List.of("Test"), disabled);
    }

    @Test
    void aProvidedSchedulerIsReadOnceAndCancelledThroughTheInjectedInstance() {
        SchedulerHolder holder = new SchedulerHolder();
        WeftLoader loader = BukkitWeft.enable(plugin, providedRegistry(true, holder));
        assertSame(scheduler, BukkitWeft.scheduler(loader));
        BukkitWeft.disable(plugin, loader);
        assertEquals(1, holder.reads);
        assertEquals(List.of("unload", "cancelAll"), events);
    }

    @Test
    void aProvidedSchedulerWithoutTheModuleIsNotManaged() {
        TestRegistry registry = new TestRegistry()
                .ambient(TestPlugin.class, Scheduler.class)
                .eager(SchedulerHolder.class, args -> new SchedulerHolder())
                .eager(Recorder.class, args -> new Recorder(true))
                .product(Scheduler.class, SchedulerHolder.class, owner -> ((SchedulerHolder) owner).scheduler());
        WeftLoader loader = BukkitWeft.enable(plugin, registry);
        assertNull(BukkitWeft.scheduler(loader));
        BukkitWeft.disable(plugin, loader);
        assertEquals(List.of("unload"), events);
    }

    @Test
    void aThrowingEnableHookTearsDownTheGraphAndDisablesThePlugin() {
        PluginLifecycle lifecycle = new PluginLifecycle(plugin);
        lifecycle.enable(() -> managed(true), () -> scheduler, () -> new Object[0], loader -> {
            assertSame(loader, lifecycle.loader());
            throw new IllegalStateException("enable failed");
        });
        assertNull(lifecycle.loader());
        assertEquals(List.of("unload", "cancelAll"), events);
        assertEquals(List.of("Test"), disabled);
        lifecycle.disable(loader -> events.add("unexpected hook"));
        assertEquals(List.of("unload", "cancelAll"), events);
    }

    @Test
    void aThrowingDisableHookStillTearsDownAndClearsTheGraph() {
        PluginLifecycle lifecycle = new PluginLifecycle(plugin);
        lifecycle.enable(() -> managed(true), () -> scheduler, () -> new Object[0], loader -> {});
        IllegalStateException failure = new IllegalStateException("disable failed");
        assertSame(
                failure,
                assertThrows(
                        IllegalStateException.class,
                        () -> lifecycle.disable(loader -> {
                            assertNotNull(loader.get(Recorder.class));
                            throw failure;
                        })));
        assertNull(lifecycle.loader());
        assertEquals(List.of("unload", "cancelAll"), events);
        lifecycle.disable(loader -> events.add("unexpected hook"));
        assertEquals(List.of("unload", "cancelAll"), events);
    }

    @Test
    void aThrowingRegistryHookDisablesThePluginBeforeLoading() {
        PluginLifecycle lifecycle = new PluginLifecycle(plugin);
        lifecycle.enable(
                () -> {
                    throw new IllegalStateException("registry failed");
                },
                () -> scheduler,
                () -> new Object[0],
                loader -> events.add("unexpected hook"));
        assertNull(lifecycle.loader());
        assertTrue(events.isEmpty());
        assertEquals(List.of("Test"), disabled);
    }

    @Test
    void aThrowingAmbientHookDisablesThePluginBeforeLoading() {
        PluginLifecycle lifecycle = new PluginLifecycle(plugin);
        lifecycle.enable(
                () -> managed(true),
                () -> scheduler,
                () -> {
                    throw new IllegalStateException("ambient failed");
                },
                loader -> events.add("unexpected hook"));
        assertNull(lifecycle.loader());
        assertTrue(events.isEmpty());
        assertEquals(List.of("Test"), disabled);
    }

    private TestRegistry registry(boolean loads) {
        return new TestRegistry().eager(Recorder.class, args -> new Recorder(loads));
    }

    private TestRegistry managed(boolean loads) {
        return new TestRegistry()
                .eager(SchedulerModule.class, args -> new SchedulerModule((Scheduler) args[0]), on(Scheduler.class))
                .eager(Recorder.class, args -> new Recorder(loads));
    }

    private TestRegistry providedRegistry(boolean loads) {
        return providedRegistry(loads, new SchedulerHolder());
    }

    private TestRegistry providedRegistry(boolean loads, SchedulerHolder holder) {
        return new TestRegistry()
                .ambient(TestPlugin.class, Scheduler.class)
                .eager(SchedulerHolder.class, args -> holder)
                .eager(SchedulerModule.class, args -> new SchedulerModule((Scheduler) args[0]), on(Scheduler.class))
                .eager(Recorder.class, args -> new Recorder(loads))
                .product(Scheduler.class, SchedulerHolder.class, owner -> ((SchedulerHolder) owner).scheduler());
    }

    private Server server() {
        Logger quiet = Logger.getAnonymousLogger();
        quiet.setUseParentHandlers(false);
        PluginManager plugins = (PluginManager) proxy(PluginManager.class, (proxy, method, args) -> {
            if (method.getName().equals("disablePlugin")) {
                disabled.add(((JavaPlugin) args[0]).getName());
                return null;
            }
            throw new UnsupportedOperationException(method.getName());
        });
        BukkitScheduler bukkit = (BukkitScheduler) proxy(BukkitScheduler.class, (proxy, method, args) -> {
            if (method.getName().equals("cancelTasks")) {
                events.add("cancelTasks");
                return null;
            }
            throw new UnsupportedOperationException(method.getName());
        });
        return (Server) proxy(Server.class, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getLogger":
                    return quiet;
                case "getPluginManager":
                    return plugins;
                case "getScheduler":
                    return bukkit;
                case "getName":
                    return "test";
                default:
                    throw new UnsupportedOperationException(method.getName());
            }
        });
    }

    private static Object proxy(Class<?> type, InvocationHandler handler) {
        return Proxy.newProxyInstance(BukkitWeftTest.class.getClassLoader(), new Class<?>[] {type}, handler);
    }

    static final class TestPlugin extends JavaPlugin {

        @SuppressWarnings("deprecation")
        TestPlugin(Server server) {
            super(
                    new JavaPluginLoader(server),
                    new PluginDescriptionFile("Test", "1.0", "test.Main"),
                    new File("build/tmp/test-plugin"),
                    new File("build/tmp/test-plugin.jar"));
        }
    }

    final class Recorder implements Loader {

        private final boolean loads;

        Recorder(boolean loads) {
            this.loads = loads;
        }

        @Override
        public boolean load() {
            return loads;
        }

        @Override
        public void unload() {
            events.add("unload");
        }
    }

    final class TestModule implements ModuleLifecycle {

        @Override
        public boolean activate() {
            events.add("activate");
            return true;
        }

        @Override
        public void deactivate() {
            events.add("deactivate");
        }
    }

    final class SchedulerHolder {

        int reads;

        Scheduler scheduler() {
            reads++;
            return scheduler;
        }
    }

    class RecordingScheduler implements Scheduler {

        @Override
        public Region global() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Region at(Location location) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Region on(Entity entity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Async async() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void cancelAll() {
            events.add("cancelAll");
        }
    }
}
