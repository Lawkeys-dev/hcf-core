package com.lawkeys.hcfcore.general.listener;

import com.lawkeys.hcfcore.general.GeneralModule;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;

/** Forgets a player's private-message state - reply target, ignore list - when they leave. */
public final class MessageListener implements Listener {

    private final GeneralModule module;

    public MessageListener(GeneralModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        module.getMessages().forget(event.getPlayer().getUniqueId());
    }
}
