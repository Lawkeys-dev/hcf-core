package com.lawkeys.hcfcore.api.event;

import com.lawkeys.hcfcore.team.Team;
import com.lawkeys.hcfcore.team.TeamRole;
import org.bukkit.event.HandlerList;

import java.util.Objects;
import java.util.UUID;

/** Fired after a member's role changed, including on a leadership handover. */
public class TeamRoleChangeEvent extends TeamEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID player;
    private final TeamRole previousRole;
    private final TeamRole newRole;

    public TeamRoleChangeEvent(Team team, UUID player, TeamRole previousRole, TeamRole newRole) {
        super(team);
        this.player = Objects.requireNonNull(player, "player");
        this.previousRole = Objects.requireNonNull(previousRole, "previousRole");
        this.newRole = Objects.requireNonNull(newRole, "newRole");
    }

    public UUID getPlayer() {
        return player;
    }

    public TeamRole getPreviousRole() {
        return previousRole;
    }

    public TeamRole getNewRole() {
        return newRole;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
