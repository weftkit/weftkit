package org.weftkit.wiring.bukkit.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.Test;

class SchedulerModuleTest {

    private int cancels;

    private final Scheduler scheduler = new Scheduler() {

        @Override
        public Region global() {
            return null;
        }

        @Override
        public Region at(Location location) {
            return null;
        }

        @Override
        public Region on(Entity entity) {
            return null;
        }

        @Override
        public Async async() {
            return null;
        }

        @Override
        public void cancelAll() {
            cancels++;
        }
    };

    @Test
    void unloadCancelsEveryTask() {
        SchedulerModule module = new SchedulerModule(scheduler);
        assertTrue(module.load());
        assertEquals(0, cancels);
        module.unload();
        assertEquals(1, cancels);
    }
}
