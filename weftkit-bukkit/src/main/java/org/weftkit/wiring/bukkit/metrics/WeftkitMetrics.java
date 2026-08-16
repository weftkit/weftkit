package org.weftkit.wiring.bukkit.metrics;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.AdvancedPie;
import org.bstats.charts.SimplePie;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.weftkit.wiring.Loader;

/**
 * Anonymous usage metrics for weftkit itself, reported to bStats under one shared service id.
 * Managed by {@link org.weftkit.wiring.bukkit.BukkitWeft} on enable and disable, so consumers
 * never create this themselves. The first weftkit plugin to enable claims a JVM-wide submitter
 * role and reports every enabled plugin that ships weftkit, recognized by the version resource in
 * its jar, so a server reports each plugin once no matter how many weftkit plugins it runs.
 * Plugin names are only reported for plugins whose main opts in with
 * {@link WeftMetrics#reportName()}, all others stay anonymous counts. Consumers opt out with
 * {@link WeftMetrics#enabled()} or by excluding org.bstats from their jar.
 */
public final class WeftkitMetrics implements Loader {

    private static final int SERVICE_ID = 33308;

    static final String VERSION_RESOURCE = "weftkit-version.properties";

    // Outside the org.weftkit namespace so shading relocation cannot rewrite the literal, which
    // must stay identical across every plugin's copy to act as the JVM-wide claim and its lock
    static final String CLAIM_KEY = "weftkit.bstats.submitter";

    // Same relocation-proof trick as CLAIM_KEY - every plugin's shaded copy registers its name
    // opt-in under this prefix, where the submitter's copy can see it
    static final String NAME_KEY_PREFIX = "weftkit.bstats.named.";

    private final JavaPlugin plugin;

    private Metrics metrics;

    private boolean submitter;

    public WeftkitMetrics(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Returns whether the plugin class disabled weftkit's metrics with {@link WeftMetrics} or the
     * deprecated {@link NoMetrics}.
     */
    @SuppressWarnings("removal")
    public static boolean optedOut(Class<?> plugin) {
        if (plugin.isAnnotationPresent(NoMetrics.class)) return true;
        WeftMetrics config = plugin.getAnnotation(WeftMetrics.class);
        return config != null && !config.enabled();
    }

    /** Returns whether the plugin class opted into name reporting with {@link WeftMetrics}. */
    public static boolean reportsName(Class<?> plugin) {
        WeftMetrics config = plugin.getAnnotation(WeftMetrics.class);
        return config != null && config.reportName();
    }

    static boolean named(String pluginName) {
        return System.getProperty(NAME_KEY_PREFIX + pluginName) != null;
    }

    @Override
    public boolean load() {
        if (reportsName(plugin.getClass())) System.setProperty(NAME_KEY_PREFIX + plugin.getName(), "true");
        // The literal is interned JVM-wide, so every plugin's shaded copy locks the same instance
        synchronized (CLAIM_KEY) {
            if (System.getProperty(CLAIM_KEY) == null) {
                System.setProperty(CLAIM_KEY, plugin.getName());
                submitter = true;
            }
        }
        if (submitter) startMetrics();
        return true;
    }

    @Override
    public void unload() {
        System.clearProperty(NAME_KEY_PREFIX + plugin.getName());
        if (submitter) releaseClaim();
        if (metrics != null) {
            metrics.shutdown();
            metrics = null;
        }
    }

    private void startMetrics() {
        try {
            metrics = new Metrics(plugin, SERVICE_ID);
            metrics.addCustomChart(new SimplePie(
                    "plugins_per_server", () -> bucket(weftkitPlugins().size())));
            metrics.addCustomChart(new AdvancedPie("named_plugins", this::countByPlugin));
            metrics.addCustomChart(new AdvancedPie("weftkit_versions", this::countByVersion));
        } catch (LinkageError ex) {
            // Missing bStats classes mean the consumer stripped them to opt out, so stay quiet
            releaseClaim();
            plugin.getLogger().log(Level.FINE, "weftkit metrics unavailable", ex);
        } catch (RuntimeException ex) {
            releaseClaim();
            plugin.getLogger().warning("Failed to start weftkit bStats integration: " + ex.getMessage());
        }
    }

    // Releasing on failure lets a weftkit plugin that enables later become the submitter instead
    private void releaseClaim() {
        synchronized (CLAIM_KEY) {
            System.clearProperty(CLAIM_KEY);
            submitter = false;
        }
    }

    private Map<String, Integer> countByPlugin() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        weftkitPlugins().keySet().forEach(name -> {
            if (named(name)) counts.put(name, 1);
        });
        return counts;
    }

    private Map<String, Integer> countByVersion() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        weftkitPlugins().values().forEach(version -> counts.merge(version, 1, Integer::sum));
        return counts;
    }

    // Shading relocates packages but not resource paths, so every plugin that ships weftkit still
    // carries the version resource at its jar root. Scanning the live plugin list for it replaces
    // any shared registry of weftkit plugins
    private Map<String, String> weftkitPlugins() {
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
