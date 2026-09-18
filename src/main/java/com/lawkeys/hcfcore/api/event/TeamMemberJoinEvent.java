package com.lawkeys.hcfcore.api.event;

import com.lawkeys.hcfcore.team.Team;
import org.bukkit.event.HandlerList;

import java.util.Objects;
import java.util.UUID;

/**
 * Fired after a player has become a member of a team.
 *
 * <p>Not cancellable: the membership is already committed by the time this
 * fires. Veto joins by cancelling {@link TeamCreateEvent} or by handling the
 * command layer instead.
 */
public class TeamMemberJoinEvent extends TeamEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID player;

    public TeamMemberJoinEvent(Team team, UUID player) {
        super(team);
        this.player = Objects.requireNonNull(player, "player");
    }

    public UUID getPlayer() {
        return player;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
