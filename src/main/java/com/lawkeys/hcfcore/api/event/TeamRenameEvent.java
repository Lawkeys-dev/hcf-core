package com.lawkeys.hcfcore.api.event;

import com.lawkeys.hcfcore.team.Team;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Fired before a team is renamed. The team still carries its old name at this
 * point; {@link #getNewName()} is the name it is about to take.
 */
public class TeamRenameEvent extends TeamEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String newName;
    private final UUID actor;
    private boolean cancelled;

    public TeamRenameEvent(Team team, String newName, UUID actor) {
        super(team);
        this.newName = Objects.requireNonNull(newName, "newName");
        this.actor = actor;
    }

    /** @return the team's current, not-yet-replaced name */
    public String getOldName() {
        return getTeam().getName();
    }

    public String getNewName() {
        return newName;
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
