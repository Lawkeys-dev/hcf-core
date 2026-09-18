package com.lawkeys.hcfcore.team.listener;

import com.lawkeys.hcfcore.api.event.TeamRaidableEvent;
import com.lawkeys.hcfcore.team.TeamModule;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.Objects;

/**
 * The Team Points scale where it is earned: deaths and kills, and a team made
 * raidable. The rules - who gets what - are {@code TeamManager#recordDeath} and
 * {@code #recordRaidable}; this only reports what happened.
 */
public final class TeamPointsListener implements Listener {

    private final TeamModule module;

    public TeamPointsListener(TeamModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        module.getManager().recordDeath(killer == null ? null : killer.getUniqueId(), event.getEntity().getUniqueId());
    }

    /** Fired by {@code dtr/} on the transition only, never for a team already raidable at start. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRaidable(TeamRaidableEvent event) {
        if (event.isRaidable()) {
            module.getManager().recordRaidable(event.getTeam());
        }
    }
}
