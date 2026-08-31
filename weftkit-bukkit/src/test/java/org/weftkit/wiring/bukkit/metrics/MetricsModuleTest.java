package org.weftkit.wiring.bukkit.metrics;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.bstats.charts.CustomChart;
import org.bstats.charts.SimplePie;
import org.junit.jupiter.api.Test;

class MetricsModuleTest {

    @Test
    void aBrokenContributorDoesNotSilenceTheOthers() {
        List<CustomChart> charts = new ArrayList<>();
        MetricsContributor broken = metrics -> {
            throw new IllegalStateException("boom");
        };
        MetricsContributor working = metrics -> metrics.add(new SimplePie("pie", () -> "value"));
        MetricsModule.contributeAll(charts::add, List.of(broken, working), quietLogger());
        assertEquals(1, charts.size());
    }

    @Test
    void everyContributorReceivesTheSameHandle() {
        List<CustomChart> charts = new ArrayList<>();
        MetricsContributor first = metrics -> metrics.add(new SimplePie("first", () -> "value"));
        MetricsContributor second = metrics -> metrics.add(new SimplePie("second", () -> "value"));
        MetricsModule.contributeAll(charts::add, List.of(first, second), quietLogger());
        assertEquals(2, charts.size());
    }

    // Pins the probed class name to the actual dependency. If bStats moves the class, this fails
    // instead of metrics silently staying off
    @Test
    void probeSeesBStatsOnTheClasspath() {
        assertTrue(MetricsModule.bStatsShipped());
    }

    @Test
    void chartsBeforeLoadAreDropped() {
        PluginBStats unloaded = new PluginBStats(null, 1);
        assertDoesNotThrow(() -> unloaded.add(new SimplePie("pie", () -> "value")));
    }

    private static Logger quietLogger() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        return logger;
    }
}
