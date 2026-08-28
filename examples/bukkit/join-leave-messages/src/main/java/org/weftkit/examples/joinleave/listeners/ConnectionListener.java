package org.weftkit.examples.joinleave.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.weftkit.examples.joinleave.messages.Messages;
import org.weftkit.wiring.Singleton;
import org.weftkit.wiring.Wired;

@Wired
@Singleton
final class ConnectionListener implements Listener {

    private final Messages messages;

    ConnectionListener(Messages messages) {
        this.messages = messages;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.setJoinMessage(messages.joined(event.getPlayer()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        event.setQuitMessage(messages.left(event.getPlayer()));
    }
}
