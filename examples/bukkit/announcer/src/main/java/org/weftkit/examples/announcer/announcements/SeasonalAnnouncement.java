package org.weftkit.examples.announcer.announcements;

import org.weftkit.examples.announcer.broadcast.Announcement;
import org.weftkit.wiring.FeatureFlag;
import org.weftkit.wiring.Singleton;
import org.weftkit.wiring.Wired;

/** Only exists while its flag is on, see ConfigFlags and config.yml. */
@Wired
@Singleton
@FeatureFlag(value = "seasonal-broadcast", enabledByDefault = false)
final class SeasonalAnnouncement implements Announcement {

    @Override
    public String text() {
        return "The harvest event runs all week, bring a hoe.";
    }
}
