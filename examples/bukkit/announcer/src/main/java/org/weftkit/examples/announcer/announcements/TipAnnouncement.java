package org.weftkit.examples.announcer.announcements;

import org.weftkit.examples.announcer.broadcast.Announcement;
import org.weftkit.wiring.Singleton;
import org.weftkit.wiring.Wired;

@Wired
@Singleton
final class TipAnnouncement implements Announcement {

    @Override
    public String text() {
        return "Trade with villagers to level them up.";
    }
}
