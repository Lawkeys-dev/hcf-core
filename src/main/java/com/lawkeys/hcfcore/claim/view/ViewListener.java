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
    private final LockWalls lockWalls;

    public ViewListener(ClientBlocks pillars, ClientBlocks walls, LockWalls lockWalls) {
        this.pillars = Objects.requireNonNull(pillars, "pillars");
        this.walls = Objects.requireNonNull(walls, "walls");
        this.lockWalls = Objects.requireNonNull(lockWalls, "lockWalls");
    }

    /**
     * A locked claim's wall is redrawn as the player walks, not on a timer: it is
     * there before they reach it. Nothing is read from the world here - the wall was
     * worked out once and kept - so this is a lookup and the difference sent.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(org.bukkit.event.player.PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            lockWalls.redraw(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        pillars.forget(event.getPlayer().getUniqueId());
        walls.forget(event.getPlayer().getUniqueId());
        lockWalls.forget(event.getPlayer().getUniqueId());
    }

    /**
     * The blocks shown were in the world left behind: forgetting them, rather than
     * sending them back, keeps the plugin from touching blocks in the new world.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        pillars.forget(event.getPlayer().getUniqueId());
        walls.forget(event.getPlayer().getUniqueId());
        lockWalls.forget(event.getPlayer().getUniqueId());
    }
}
