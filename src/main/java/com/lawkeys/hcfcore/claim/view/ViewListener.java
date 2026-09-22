package com.lawkeys.hcfcore.claim.view;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;

/** Forgets what a player was being shown when they leave, or change world. */
public final class ViewListener implements Listener {

    private final ClientBlocks pillars;
    private final ClientBlocks walls;

    public ViewListener(ClientBlocks pillars, ClientBlocks walls) {
        this.pillars = Objects.requireNonNull(pillars, "pillars");
        this.walls = Objects.requireNonNull(walls, "walls");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        pillars.forget(event.getPlayer().getUniqueId());
        walls.forget(event.getPlayer().getUniqueId());
    }

    /**
     * The blocks shown were in the world left behind: forgetting them, rather than
     * sending them back, keeps the plugin from touching blocks in the new world.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        pillars.forget(event.getPlayer().getUniqueId());
        walls.forget(event.getPlayer().getUniqueId());
    }
}
