package org.weftkit.wiring.bukkit.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.Test;

class MetricsDescriptorTest {

    private final FakeServices services = new FakeServices();

    @Test
    void firstClaimingCopySubmits() {
        services.register(Properties.class, new Properties(), plugin("Unrelated"), ServicePriority.Normal);
        MetricsDescriptor first = descriptor("A", false);
        MetricsDescriptor second = descriptor("B", false);
        first.register();
        assertTrue(first.claimSubmit());
        second.register();
        assertFalse(second.claimSubmit());
    }

    // Regression: equal-priority registrations do not keep registration order in the services
    // manager, so a later copy sorting before the claimant must still lose
    @Test
    void laterCopySortedFirstStillLoses() {
        MetricsDescriptor first = descriptor("A", false);
        first.register();
        assertTrue(first.claimSubmit());
        MetricsDescriptor second = descriptor("B", false);
        second.register();
        assertTrue(services.firstProviderOf(Properties.class) == services.lastRegistered);
        assertFalse(second.claimSubmit());
    }

    @Test
    void claimIsStableForItsHolder() {
        MetricsDescriptor only = descriptor("A", false);
        only.register();
        assertTrue(only.claimSubmit());
        assertTrue(only.claimSubmit());
    }

    @Test
    void nextCopyTakesOverWhenTheSubmitterUnregisters() {
        MetricsDescriptor first = descriptor("A", false);
        MetricsDescriptor second = descriptor("B", false);
        first.register();
        first.claimSubmit();
        second.register();
        first.unregister();
        assertTrue(second.handover(services.lastUnregistered));
        assertTrue(second.claimSubmit());
    }

    @Test
    void withdrawnCopyHandsOverAndStaysNamed() {
        MetricsDescriptor first = descriptor("A", true);
        MetricsDescriptor second = descriptor("B", false);
        first.register();
        first.claimSubmit();
        second.register();
        first.withdrawSubmit();
        assertTrue(second.handover(services.lastUnregistered));
        assertFalse(first.claimSubmit());
        assertTrue(second.claimSubmit());
        assertEquals(List.of("A"), second.namedPlugins());
    }

    @Test
    void lastCopyWithdrawingLeavesNoSubmitter() {
        MetricsDescriptor only = descriptor("A", true);
        only.register();
        only.claimSubmit();
        only.withdrawSubmit();
        assertFalse(only.claimSubmit());
    }

    @Test
    void ownUnregistrationIsNoHandover() {
        MetricsDescriptor only = descriptor("A", false);
        only.register();
        assertFalse(only.handover(services.lastRegistered));
    }

    private MetricsDescriptor descriptor(String pluginName, boolean reportName) {
        return new MetricsDescriptor(plugin(pluginName), reportName);
    }

    private Plugin plugin(String name) {
        return PluginStubs.named(name, PluginStubs.server(services));
    }

    // Mirrors SimpleServicesManager's binary search insertion, which does not keep
    // equal-priority registrations in registration order
    private static final class FakeServices implements ServicesManager {

        private final List<RegisteredServiceProvider<?>> registrations = new ArrayList<>();

        private Object lastRegistered;

        private Object lastUnregistered;

        @Override
        public <T> void register(Class<T> service, T provider, Plugin plugin, ServicePriority priority) {
            RegisteredServiceProvider<T> registration =
                    new RegisteredServiceProvider<>(service, provider, priority, plugin);
            int position = Collections.binarySearch(registrations, registration);
            registrations.add(position < 0 ? -(position + 1) : position, registration);
            lastRegistered = provider;
        }

        @Override
        public void unregister(Class<?> service, Object provider) {
            registrations.removeIf(registration -> registration.getProvider() == provider);
            lastUnregistered = provider;
        }

        @Override
        public void unregister(Object provider) {
            throw new UnsupportedOperationException();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Collection<RegisteredServiceProvider<T>> getRegistrations(Class<T> service) {
            List<RegisteredServiceProvider<T>> matching = new ArrayList<>();
            for (RegisteredServiceProvider<?> registration : registrations) {
                if (registration.getService() == service) matching.add((RegisteredServiceProvider<T>) registration);
            }
            return matching;
        }

        Object firstProviderOf(Class<?> service) {
            for (RegisteredServiceProvider<?> registration : registrations) {
                if (registration.getService() == service) return registration.getProvider();
            }
            return null;
        }

        @Override
        public void unregisterAll(Plugin plugin) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T load(Class<T> service) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> RegisteredServiceProvider<T> getRegistration(Class<T> service) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<RegisteredServiceProvider<?>> getRegistrations(Plugin plugin) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Collection<Class<?>> getKnownServices() {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> boolean isProvidedFor(Class<T> service) {
            throw new UnsupportedOperationException();
        }
    }
}
