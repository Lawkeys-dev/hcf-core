package com.lawkeys.hcfcore.api.event;

import com.lawkeys.hcfcore.team.Team;
import org.bukkit.event.HandlerList;

import java.util.Objects;

/**
 * Fired after two teams became allies, or stopped being allies.
 *
 * <p>Fired once for the pair, not once per side; {@link #getTeam()} is the team
 * that acted and {@link #getOtherTeam()} the one it acted on.
 */
public class TeamAllianceChangeEvent extends TeamEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Team otherTeam;
    private final boolean allied;

    public TeamAllianceChangeEvent(Team team, Team otherTeam, boolean allied) {
        super(team);
        this.otherTeam = Objects.requireNonNull(otherTeam, "otherTeam");
        this.allied = allied;
    }

    public Team getOtherTeam() {
        return otherTeam;
    }

    /** @return {@code true} if an alliance was formed, {@code false} if one was broken */
    public boolean isAllied() {
        return allied;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
