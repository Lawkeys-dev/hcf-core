package com.lawkeys.hcfcore.api.event;

import com.lawkeys.hcfcore.team.Team;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

import java.util.Optional;
import java.util.UUID;

/**
 * Fired before a team is disbanded. Cancelling keeps the team intact.
 *
 * <p>Modules holding team-keyed data (claims, DTR) should listen to this to
 * release it once the disband actually goes through.
 */
public class TeamDisbandEvent extends TeamEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID actor;
    private boolean cancelled;

    /** @param actor who disbanded the team, or {@code null} for a staff/console override */
    public TeamDisbandEvent(Team team, UUID actor) {
        super(team);
        this.actor = actor;
    }

    public Optional<UUID> getActor() {
        return Optional.ofNullable(actor);
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
