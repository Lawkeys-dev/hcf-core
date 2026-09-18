package com.lawkeys.hcfcore.api.event;

import com.lawkeys.hcfcore.team.Team;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

import java.util.Optional;
import java.util.UUID;

/**
 * Fired before a team is created. Cancelling aborts the creation entirely - the
 * team is never registered and the creator keeps no membership.
 */
public class TeamCreateEvent extends TeamEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID creator;
    private boolean cancelled;

    /** @param creator the founding player, or {@code null} for a staff-created system team */
    public TeamCreateEvent(Team team, UUID creator) {
        super(team);
        this.creator = creator;
    }

    public Optional<UUID> getCreator() {
        return Optional.ofNullable(creator);
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
