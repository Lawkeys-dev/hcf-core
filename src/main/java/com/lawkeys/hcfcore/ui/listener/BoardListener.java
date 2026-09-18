package com.lawkeys.hcfcore.ui.listener;

import com.lawkeys.hcfcore.ui.UiModule;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;

/** Gives every player a board when they arrive, and forgets it when they leave. */
public final class BoardListener implements Listener {

    private final UiModule module;

    public BoardListener(UiModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        module.attach(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        module.detach(event.getPlayer().getUniqueId());
    }
}
