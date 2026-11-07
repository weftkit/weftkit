package org.weftkit.examples.announcer;

import org.weftkit.wiring.Singleton;
import org.weftkit.wiring.Wired;
import org.weftkit.wiring.bukkit.commands.CommandHandler;
import org.weftkit.wiring.bukkit.diagnostics.DiagnosticsCommand;
import org.weftkit.wiring.runtime.WeftDiagnostics;

@Wired
@Singleton
@CommandHandler("announcerdebug")
final class AnnouncerDiagnostics extends DiagnosticsCommand {
    AnnouncerDiagnostics(WeftDiagnostics diagnostics) {
        super(diagnostics);
    }
}
