package org.weftkit.wiring.folia;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.weftkit.wiring.bukkit.scheduler.Scheduler;
import org.weftkit.wiring.bukkit.scheduler.Schedulers;

class FoliaSchedulersTest {

    private final Plugin plugin = FoliaStubs.plugin(null);

    @Test
    void detectsNoFoliaFromTheApiJarAlone() {
        assertFalse(Schedulers.isFolia());
    }

    @Test
    void detectFallsBackToTheBukkitScheduler() {
        Scheduler detected = FoliaSchedulers.detect(plugin);
        assertNotNull(detected);
        assertFalse(detected instanceof FoliaScheduling);
    }
}
