package com.lawkeys.hcfcore.claim.listener;

import com.lawkeys.hcfcore.api.event.TeamDisbandEvent;
import com.lawkeys.hcfcore.claim.ClaimModule;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Objects;

/**
 * Frees a disbanded team's territory.
 *
 * <p>This is the claim module consuming the team module's public API rather than
 * the two calling into each other (ARCHITECTURE.md section 7). It listens at
 * {@link EventPriority#MONITOR} with {@code ignoreCancelled}, so the land is only
 * released once the disband has actually gone through.
 */
public final class TeamDisbandClaimListener implements Listener {

    private final ClaimModule module;

    public TeamDisbandClaimListener(ClaimModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    /**
     * {@code TeamDisbandEvent} is cancellable and fires before the team is removed,
     * so MONITOR with {@code ignoreCancelled} is the point where the disband is
     * known to be going ahead: nothing runs after MONITOR to veto it.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onTeamDisband(TeamDisbandEvent event) {
        if (module.getManager() == null) {
            return;
        }
        module.getManager().releaseAll(event.getTeam().getId());
    }
}
