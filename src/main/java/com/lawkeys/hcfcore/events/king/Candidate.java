package com.lawkeys.hcfcore.events.king;

import java.util.Objects;
import java.util.UUID;

/**
 * An online player who may be drawn as King.
 *
 * @param teamId their team, or {@code null} for a player with none
 */
public record Candidate(UUID playerId, UUID teamId) {

    public Candidate {
        Objects.requireNonNull(playerId, "playerId");
    }
}
