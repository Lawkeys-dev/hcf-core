package com.lawkeys.hcfcore.api.event;

import com.lawkeys.hcfcore.team.Team;
import org.bukkit.event.HandlerList;

/**
 * Fired when a team's territory becomes raidable, or stops being.
 *
 * <p>Named in ARCHITECTURE.md section 7 as one of the events the core should
 * expose. Not cancellable: raidability is derived from the team's current DTR, so
 * there is no decision here to veto - this reports a transition that has already
 * happened.
 *
 * <p><strong>It reports the DTR, not the map.</strong> EOTW and the Purge make
 * every team raidable whatever its DTR, and fire nothing here, starting or ending;
 * a DTR transition during them still fires it (the team points of
 * {@code per-raidable} are about a team brought down by its DTR). Whether claims
 * are open right now is the claim module's {@code RaidabilityPolicy}.
 *
 * <p>Note when this fires. Becoming raidable is caused by a death, so it fires
 * immediately. Ceasing to be raidable is caused by time passing, which nothing
 * observes on its own, so it fires from the DTR module's announcement poll and is
 * therefore accurate only to that poll's interval.
 */
public class TeamRaidableEvent extends TeamEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final boolean raidable;

    public TeamRaidableEvent(Team team, boolean raidable) {
        super(team);
        this.raidable = raidable;
    }

    /** @return {@code true} if the team just became raidable, {@code false} if it just stopped */
    public boolean isRaidable() {
        return raidable;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
