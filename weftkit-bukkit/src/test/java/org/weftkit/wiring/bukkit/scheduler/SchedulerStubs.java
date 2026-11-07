package org.weftkit.wiring.bukkit.scheduler;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

/** Recording Bukkit stubs for the scheduler tests, throwing on any method a test did not stub. */
final class SchedulerStubs {

    record Call(String method, Object[] args) {}

    static final class RecordingScheduler {

        final List<Call> calls = new ArrayList<>();

        final List<Runnable> bodies = new ArrayList<>();

        final List<int[]> cancels = new ArrayList<>();

        int cancelTasksCalls;

        int runningTask = -1;

        RuntimeException failure;

        Runnable duringSchedule;

        BukkitScheduler scheduler() {
            return (BukkitScheduler) proxy(BukkitScheduler.class, (proxy, method, args) -> {
                if (method.getName().equals("cancelTasks")) {
                    cancelTasksCalls++;
                    return null;
                }
                if (method.getName().equals("isQueued"))
                    return (int) args[0] != runningTask && cancels.get((int) args[0])[0] == 0;
                if (method.getName().equals("isCurrentlyRunning")) return (int) args[0] == runningTask;
                if (failure != null) throw failure;
                Object[] rest = new Object[args.length - 2];
                System.arraycopy(args, 2, rest, 0, rest.length);
                calls.add(new Call(method.getName(), rest));
                bodies.add((Runnable) args[1]);
                int id = cancels.size();
                int[] cancelled = new int[1];
                cancels.add(cancelled);
                if (duringSchedule != null) duringSchedule.run();
                return proxy(BukkitTask.class, (p, m, a) -> {
                    if (m.getName().equals("cancel")) {
                        cancelled[0]++;
                        return null;
                    }
                    if (m.getName().equals("getTaskId")) return id;
                    throw new UnsupportedOperationException(m.getName());
                });
            });
        }
    }

    private SchedulerStubs() {}

    static Plugin plugin(boolean primaryThread) {
        Server server = (Server) proxy(Server.class, (proxy, method, args) -> {
            if (method.getName().equals("isPrimaryThread")) return primaryThread;
            throw new UnsupportedOperationException(method.getName());
        });
        return (Plugin) proxy(Plugin.class, (proxy, method, args) -> {
            if (method.getName().equals("getServer")) return server;
            throw new UnsupportedOperationException(method.getName());
        });
    }

    static Object proxy(Class<?> type, InvocationHandler handler) {
        return Proxy.newProxyInstance(SchedulerStubs.class.getClassLoader(), new Class<?>[] {type}, handler);
    }
}
