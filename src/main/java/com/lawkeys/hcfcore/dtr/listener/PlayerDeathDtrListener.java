package com.lawkeys.hcfcore.dtr.listener;

import com.lawkeys.hcfcore.api.event.TeamMemberLeaveEvent;
import com.lawkeys.hcfcore.api.event.TeamDisbandEvent;
import com.lawkeys.hcfcore.dtr.DtrModule;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.Objects;

/**
 * Charges a team's DTR when one of its members dies, and drops DTR state when a
 * team is disbanded.
 *
 * <p>Holds no rules: it resolves the dead player's team and hands off to
 * {@code DtrManager}. Listening at {@link EventPriority#MONITOR} keeps it out of
 * the way of anything that might cancel or alter the death first.
 */
public final class PlayerDeathDtrListener implements Listener {

    private final DtrModule module;

    public PlayerDeathDtrListener(DtrModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    /** A death another plugin cancels is not one - the player is revived - and costs nothing. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (module.getManager() == null) {
            return;
        }
        Player player = event.getEntity();
        module.getTeams().getManager().getTeamOf(player.getUniqueId())
                .ifPresent(team -> module.applyDeath(team, player.getName()));
    }

    /** A disbanded team's DTR row would otherwise outlive it. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onTeamDisband(TeamDisbandEvent event) {
        if (module.getManager() != null) {
            module.getManager().release(event.getTeam().getId());
        }
    }

    /**
     * A team losing a member loses DTR ceiling with it. Nothing to store - the
     * ceiling is derived from the member count on read - but the announcement poll
     * should notice if that pushed the team into being raidable.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onMemberLeave(TeamMemberLeaveEvent event) {
        module.pollAnnouncements();
    }
}
