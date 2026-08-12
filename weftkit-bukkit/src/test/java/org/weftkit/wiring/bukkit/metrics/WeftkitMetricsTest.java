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

    @NoMetrics
    private static final class OptedOutPlugin {}

    private static final class ReportingPlugin {}

    @Test
    void noMetricsAnnotationOptsThePluginOut() {
        assertTrue(WeftkitMetrics.optedOut(OptedOutPlugin.class));
        assertFalse(WeftkitMetrics.optedOut(ReportingPlugin.class));
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
