package org.weftkit.wiring.bukkit.metrics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;

/**
 * This copy's entry in the server's services manager, through which the shaded weftkit copies
 * coordinate one bStats submitter per server. Keyed by a JDK type, which relocation cannot split
 * apart across plugins.
 */
final class MetricsDescriptor {

    private static final String SUBMIT_KEY = "weftkit.bstats.submit";

    private static final String SUBMITTING_KEY = "weftkit.bstats.submitting";

    private static final String NAMED_KEY = "weftkit.bstats.named";

    private final Plugin plugin;

    private final boolean reportName;

    private Properties entry;

    MetricsDescriptor(Plugin plugin, boolean reportName) {
        this.plugin = plugin;
        this.reportName = reportName;
    }

    void register() {
        entry = publish(true);
    }

    void unregister() {
        if (entry == null) return;
        Properties gone = entry;
        entry = null;
        services().unregister(Properties.class, gone);
    }

    void withdrawSubmit() {
        unregister();
        if (reportName) entry = publish(false);
    }

    boolean claimSubmit() {
        if (entry == null || !entry.containsKey(SUBMIT_KEY)) return false;
        // The services manager is the only object every relocated copy shares, so it doubles as
        // the election mutex
        synchronized (services()) {
            for (RegisteredServiceProvider<Properties> registration : registrations()) {
                if (registration.getProvider().containsKey(SUBMITTING_KEY)) return registration.getProvider() == entry;
            }
            entry.setProperty(SUBMITTING_KEY, "true");
        }
        return true;
    }

    boolean handover(Object gone) {
        return gone != entry && gone instanceof Properties revoked && revoked.containsKey(SUBMIT_KEY);
    }

    List<String> namedPlugins() {
        List<String> names = new ArrayList<>();
        for (RegisteredServiceProvider<Properties> registration : registrations()) {
            if (registration.getProvider().containsKey(NAMED_KEY))
                names.add(registration.getPlugin().getName());
        }
        return names;
    }

    private Properties publish(boolean submit) {
        Properties published = new Properties();
        if (submit) published.setProperty(SUBMIT_KEY, "true");
        if (reportName) published.setProperty(NAMED_KEY, "true");
        services().register(Properties.class, published, plugin, ServicePriority.Lowest);
        return published;
    }

    private Collection<RegisteredServiceProvider<Properties>> registrations() {
        return services().getRegistrations(Properties.class);
    }

    private ServicesManager services() {
        return plugin.getServer().getServicesManager();
    }
}
