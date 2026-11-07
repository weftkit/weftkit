package org.weftkit.wiring.folia;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.weftkit.wiring.bukkit.scheduler.Scheduler;

class SchedulerDetectionTest {

    private final List<String> calls = new ArrayList<>();

    private final Scheduler detected = (Scheduler) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[] {Scheduler.class}, (proxy, method, args) -> {
                calls.add(method.getName());
                return null;
            });

    @Test
    void providesTheDetectedScheduler() {
        SchedulerDetection detection = new SchedulerDetection(detected);
        assertSame(detected, detection.scheduler());
        assertTrue(calls.isEmpty());
    }
}
