package com.lawkeys.hcfcore.claim;

import com.lawkeys.hcfcore.util.ChunkPosition;

import java.util.Objects;
import java.util.UUID;

/**
 * One chunk owned by one team.
 *
 * <p>Ownership is the persistent half of the model FEATURES.md section 3 calls
 * for: it is set when the chunk is claimed and only cleared by an explicit
 * unclaim or a disband. It is never affected by raiding - whether the owner can
 * currently be raided is a separate, dynamic question answered by
 * {@link RaidabilityPolicy}.
 */
public record Claim(UUID teamId, ChunkPosition chunk, long claimedAt) {

    public Claim {
        Objects.requireNonNull(teamId, "teamId");
        Objects.requireNonNull(chunk, "chunk");
    }
}
