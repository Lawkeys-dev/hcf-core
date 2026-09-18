package com.lawkeys.hcfcore.pvp.listener;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import com.lawkeys.hcfcore.pvp.PvpModule;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Objects;

/**
 * Gives every player the configured attack speed when they arrive, and again when
 * they respawn.
 *
 * <p>The modifier is transient - never saved - so it has to be given each time the
 * player entity is built, which is at join and at respawn. {@code PlayerPostRespawnEvent}
 * ("fired after a player has respawned", Paper 26.2) rather than the Bukkit respawn
 * event, which fires before the new entity is in the world.
 */
public final class AttackSpeedListener implements Listener {

    private final PvpModule module;

    public AttackSpeedListener(PvpModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        module.applyAttackSpeed(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerPostRespawnEvent event) {
        module.applyAttackSpeed(event.getPlayer());
    }
}
