package org.weftkit.examples.announcer.broadcast;

import org.weftkit.wiring.ModuleSettings;

/** Configuration for {@link AnnouncerModule}. No implementation is wired, the defaults apply. */
@ModuleSettings
public interface AnnouncerSettings {

    /** Ticks between two broadcasts. */
    default long intervalTicks() {
        return 20L * 60;
    }
}
