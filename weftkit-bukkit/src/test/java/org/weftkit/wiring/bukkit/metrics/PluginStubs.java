package org.weftkit.wiring.bukkit.metrics;

import java.io.ByteArrayInputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicesManager;

/** Dynamic Bukkit stubs for the metrics tests, throwing on any method a test did not stub. */
final class PluginStubs {

    private PluginStubs() {}

    static Plugin named(String name, Server server) {
        return (Plugin) proxy(Plugin.class, (proxy, method, args) -> {
            if (method.getName().equals("getName")) return name;
            if (method.getName().equals("getServer")) return server;
            throw new UnsupportedOperationException(method.getName());
        });
    }

    static Plugin withResource(String content) {
        return (Plugin) proxy(Plugin.class, (proxy, method, args) -> {
            if (!method.getName().equals("getResource")) throw new UnsupportedOperationException(method.getName());
            return content == null ? null : new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        });
    }

    static Server server(ServicesManager services) {
        return (Server) proxy(Server.class, (proxy, method, args) -> {
            if (method.getName().equals("getServicesManager")) return services;
            throw new UnsupportedOperationException(method.getName());
        });
    }

    private static Object proxy(Class<?> type, InvocationHandler handler) {
        return Proxy.newProxyInstance(PluginStubs.class.getClassLoader(), new Class<?>[] {type}, handler);
    }
}
