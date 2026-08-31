package org.weftkit.wiring.bukkit.metrics;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.AdvancedPie;
import org.bstats.charts.SimplePie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServiceUnregisterEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.weftkit.wiring.Loader;

/**
 * Anonymous usage metrics for weftkit itself, reported to bStats under one shared service id by
 * one plugin per server, see {@link MetricsDescriptor}. Managed by the bundled
 * {@link MetricsModule}. Plugin names are only reported on opt-in through {@link MetricsSettings}.
 */
final class WeftkitMetrics implements Loader, Listener {

    private static final int SERVICE_ID = 33308;

    private static final long SNAPSHOT_TTL_MILLIS = 10_000;

    static final String VERSION_RESOURCE = "weftkit-version.properties";

    private final JavaPlugin plugin;

    private final MetricsDescriptor descriptor;

    private Metrics metrics;

    private Map<String, String> snapshot;

    private long snapshotAt;

    WeftkitMetrics(JavaPlugin plugin, boolean reportName) {
        this.plugin = plugin;
        this.descriptor = new MetricsDescriptor(plugin, reportName);
    }

    @Override
    public boolean load() {
        descriptor.register();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        claimAndStart();
        return true;
    }

    @Override
    public void unload() {
        HandlerList.unregisterAll(this);
        stop();
        descriptor.unregister();
    }

    @EventHandler
    public void onServiceUnregister(ServiceUnregisterEvent event) {
        if (!plugin.isEnabled()) return;
        if (!descriptor.handover(event.getProvider().getProvider())) return;
        // Next tick, not inline: a stopping server no longer ticks, so the role does not churn
        // through copies that are about to disable with it
        plugin.getServer().getScheduler().runTask(plugin, this::claimAndStart);
    }

    private void claimAndStart() {
        if (metrics == null && descriptor.claimSubmit()) startMetrics();
    }

    private void startMetrics() {
        try {
            metrics = new Metrics(plugin, SERVICE_ID);
            metrics.addCustomChart(new SimplePie(
                    "plugins_per_server", () -> bucket(weftkitPlugins().size())));
            metrics.addCustomChart(new AdvancedPie("named_plugins", this::countByPlugin));
            metrics.addCustomChart(new AdvancedPie("weftkit_versions", this::countByVersion));
        } catch (RuntimeException | LinkageError ex) {
            // Withdraw so another copy can claim the submit role
            withdraw();
            plugin.getLogger().log(Level.WARNING, "Failed to start weftkit bStats integration", ex);
        }
    }

    private void withdraw() {
        stop();
        descriptor.withdrawSubmit();
    }

    private void stop() {
        if (metrics == null) return;
        metrics.shutdown();
        metrics = null;
    }

    private Map<String, Integer> countByPlugin() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Set<String> weftkit = weftkitPlugins().keySet();
        for (String name : descriptor.namedPlugins()) {
            if (weftkit.contains(name)) counts.put(name, 1);
        }
        return counts;
    }

    private Map<String, Integer> countByVersion() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        weftkitPlugins().values().forEach(version -> counts.merge(version, 1, Integer::sum));
        return counts;
    }

    private Map<String, String> weftkitPlugins() {
        long now = System.currentTimeMillis();
        if (snapshot == null || now - snapshotAt > SNAPSHOT_TTL_MILLIS) {
            snapshot = scanPlugins();
            snapshotAt = now;
        }
        return snapshot;
    }

    private Map<String, String> scanPlugins() {
        Map<String, String> plugins = new LinkedHashMap<>();
        for (Plugin candidate : plugin.getServer().getPluginManager().getPlugins()) {
            if (!candidate.isEnabled()) continue;
            String version = version(candidate);
            if (version != null) plugins.put(candidate.getName(), version);
        }
        return plugins;
    }

    static String version(Plugin candidate) {
        try (InputStream stream = candidate.getResource(VERSION_RESOURCE)) {
            return stream == null ? null : parseVersion(stream);
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }

    static String parseVersion(InputStream stream) throws IOException {
        Properties properties = new Properties();
        properties.load(stream);
        return properties.getProperty("version", "unknown");
    }

    static String bucket(int count) {
        if (count <= 0) return "0";
        if (count >= 10) return "10+";
        return String.valueOf(count);
    }
}
