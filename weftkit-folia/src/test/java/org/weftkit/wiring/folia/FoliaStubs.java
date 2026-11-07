package org.weftkit.wiring.folia;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

/** Recording Folia stubs, throwing on any method a test did not stub. */
final class FoliaStubs {

    record Call(String method, Object[] args) {}

    static final class Recording {

        final List<Call> calls = new ArrayList<>();

        final List<Consumer<ScheduledTask>> bodies = new ArrayList<>();

        final List<Runnable> retireHooks = new ArrayList<>();

        final List<int[]> cancels = new ArrayList<>();

        int cancelTasksCalls;

        boolean retired;

        Runnable duringSchedule;

        @SuppressWarnings("unchecked")
        <T> T as(Class<T> type) {
            return (T) proxy(type, (proxy, method, args) -> {
                if (method.getName().equals("cancelTasks")) {
                    cancelTasksCalls++;
                    return null;
                }
                List<Object> rest = new ArrayList<>();
                Consumer<ScheduledTask> body = null;
                for (int i = 1; i < args.length; i++) {
                    if (body == null && args[i] instanceof Consumer) {
                        body = (Consumer<ScheduledTask>) args[i];
                    } else if (args[i] instanceof Runnable hook) {
                        retireHooks.add(hook);
                    } else {
                        rest.add(args[i]);
                    }
                }
                calls.add(new Call(method.getName(), rest.toArray()));
                bodies.add(body);
                if (retired) return null;
                int[] cancelled = new int[1];
                cancels.add(cancelled);
                if (duringSchedule != null) duringSchedule.run();
                return proxy(ScheduledTask.class, (p, m, a) -> {
                    if (m.getName().equals("cancel")) {
                        cancelled[0]++;
                        return null;
                    }
                    if (m.getName().equals("isCancelled")) return cancelled[0] > 0;
                    throw new UnsupportedOperationException(m.getName());
                });
            });
        }
    }

    private FoliaStubs() {}

    static Entity entity(EntityScheduler scheduler) {
        return (Entity) proxy(Entity.class, (proxy, method, args) -> {
            if (method.getName().equals("getScheduler")) return scheduler;
            throw new UnsupportedOperationException(method.getName());
        });
    }

    static Plugin plugin(Server server) {
        return (Plugin) proxy(Plugin.class, (proxy, method, args) -> {
            if (method.getName().equals("getServer")) return server;
            throw new UnsupportedOperationException(method.getName());
        });
    }

    static Object proxy(Class<?> type, InvocationHandler handler) {
        return Proxy.newProxyInstance(FoliaStubs.class.getClassLoader(), new Class<?>[] {type}, handler);
    }
}
