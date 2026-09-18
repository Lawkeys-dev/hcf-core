package com.lawkeys.hcfcore.api.event;

import com.lawkeys.hcfcore.team.Team;
import com.lawkeys.hcfcore.team.TeamEventDispatcher;
import org.bukkit.event.HandlerList;

import java.util.Objects;
import java.util.UUID;

/**
 * Fired after a player has stopped being a member of a team, whatever the
 * reason ({@link TeamEventDispatcher.LeaveCause}).
 *
 * <p>When a team is disbanded this fires once per member, before the team itself
 * is removed from the cache.
 */
public class TeamMemberLeaveEvent extends TeamEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID player;
    private final TeamEventDispatcher.LeaveCause cause;

    public TeamMemberLeaveEvent(Team team, UUID player, TeamEventDispatcher.LeaveCause cause) {
        super(team);
        this.player = Objects.requireNonNull(player, "player");
        this.cause = Objects.requireNonNull(cause, "cause");
    }

    public UUID getPlayer() {
        return player;
    }

    public TeamEventDispatcher.LeaveCause getCause() {
        return cause;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
