package com.lawkeys.hcfcore.events;

import java.util.Objects;
import java.util.UUID;

/**
 * A player standing inside a capture zone at the moment of a tick.
 *
 * <p>The rule engine never touches the server, so the server layer resolves who
 * is where and hands the answer in as a list of these. That is also what makes
 * the capture rules testable: a test just describes who is in the zone.
 *
 * @param playerId the player
 * @param teamId   their team, or {@code null} when they have none - a teamless
 *                 player contests a capture but can never hold one
 */
public record Occupant(UUID playerId, UUID teamId) {

    public Occupant {
        Objects.requireNonNull(playerId, "playerId");
    }

    public static Occupant of(UUID playerId, UUID teamId) {
        return new Occupant(playerId, teamId);
    }

    public boolean hasTeam() {
        return teamId != null;
    }
}
