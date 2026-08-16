package org.weftkit.wiring.bukkit.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class WeftkitMetricsTest {

    @Test
    void readsVersionFromPluginResource() {
        assertEquals("1.2.3", WeftkitMetrics.version(pluginWithResource("version=1.2.3\n")));
    }

    @Test
    void missingVersionKeyReadsUnknown() {
        assertEquals("unknown", WeftkitMetrics.version(pluginWithResource("other=value\n")));
    }

    @Test
    void pluginWithoutResourceHasNoVersion() {
        assertNull(WeftkitMetrics.version(pluginWithResource(null)));
    }

    @WeftMetrics(enabled = false)
    private static final class OptedOutPlugin {}

    private static final class UnannotatedPlugin {}

    @WeftMetrics
    private static final class DefaultConfiguredPlugin {}

    @WeftMetrics(reportName = true)
    private static final class NamedPlugin {}

    @SuppressWarnings("removal")
    @NoMetrics
    private static final class LegacyOptedOutPlugin {}

    @Test
    void metricsAnnotationDisablesReporting() {
        assertTrue(WeftkitMetrics.optedOut(OptedOutPlugin.class));
        assertFalse(WeftkitMetrics.optedOut(UnannotatedPlugin.class));
        assertFalse(WeftkitMetrics.optedOut(DefaultConfiguredPlugin.class));
        assertFalse(WeftkitMetrics.optedOut(NamedPlugin.class));
    }

    @Test
    void deprecatedNoMetricsAnnotationStillOptsOut() {
        assertTrue(WeftkitMetrics.optedOut(LegacyOptedOutPlugin.class));
    }

    @Test
    void metricsAnnotationOptsIntoNameReporting() {
        assertTrue(WeftkitMetrics.reportsName(NamedPlugin.class));
        assertFalse(WeftkitMetrics.reportsName(OptedOutPlugin.class));
        assertFalse(WeftkitMetrics.reportsName(UnannotatedPlugin.class));
        assertFalse(WeftkitMetrics.reportsName(DefaultConfiguredPlugin.class));
    }

    @Test
    void nameReportingFollowsTheRegisteredOptIn() {
        String key = WeftkitMetrics.NAME_KEY_PREFIX + "SomePlugin";
        try {
            assertFalse(WeftkitMetrics.named("SomePlugin"));
            System.setProperty(key, "true");
            assertTrue(WeftkitMetrics.named("SomePlugin"));
        } finally {
            System.clearProperty(key);
        }
    }

    @Test
    void bucketsClampToRange() {
        assertEquals("0", WeftkitMetrics.bucket(-1));
        assertEquals("0", WeftkitMetrics.bucket(0));
        assertEquals("1", WeftkitMetrics.bucket(1));
        assertEquals("9", WeftkitMetrics.bucket(9));
        assertEquals("10+", WeftkitMetrics.bucket(10));
        assertEquals("10+", WeftkitMetrics.bucket(25));
    }

    private static Plugin pluginWithResource(String content) {
        return (Plugin) Proxy.newProxyInstance(
                WeftkitMetricsTest.class.getClassLoader(), new Class<?>[] {Plugin.class}, (proxy, method, args) -> {
                    if (!method.getName().equals("getResource"))
                        throw new UnsupportedOperationException(method.getName());
                    return content == null ? null : asStream(content);
                });
    }

    private static InputStream asStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
