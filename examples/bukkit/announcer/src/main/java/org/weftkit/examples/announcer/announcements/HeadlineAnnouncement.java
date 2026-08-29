package org.weftkit.examples.announcer.announcements;

import java.util.Optional;
import org.weftkit.examples.announcer.broadcast.Announcement;
import org.weftkit.wiring.Singleton;
import org.weftkit.wiring.Wired;

/**
 * Depends on the flagged {@link SeasonalAnnouncement} directly, rather than through enumeration. A
 * required dependency on a flagged component is rejected at compile time, so the type is
 * {@code Optional<SeasonalAnnouncement>}: it holds the component while the flag is on and is empty
 * while it is off, letting this line reuse the seasonal text when there is one and fall back when
 * there is not.
 */
@Wired
@Singleton
final class HeadlineAnnouncement implements Announcement {

    private final Optional<SeasonalAnnouncement> seasonal;

    HeadlineAnnouncement(Optional<SeasonalAnnouncement> seasonal) {
        this.seasonal = seasonal;
    }

    @Override
    public String text() {
        return seasonal
                .map(event -> "Headline: " + event.text())
                .orElse("Headline: a quiet week on the server, no events running.");
    }
}
