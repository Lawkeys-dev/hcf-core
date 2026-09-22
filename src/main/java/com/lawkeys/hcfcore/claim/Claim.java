package com.lawkeys.hcfcore.claim;

import com.lawkeys.hcfcore.util.ChunkPosition;

import java.util.Objects;
import java.util.UUID;

/**
 * One chunk owned by one team - a claim as versions up to 0.7 stored it, read
 * once to be turned into {@link ClaimArea block claims} by
 * {@link LegacyChunkClaims}, then no more.
 */
public record Claim(UUID teamId, ChunkPosition chunk, long claimedAt) {

    public Claim {
        Objects.requireNonNull(teamId, "teamId");
        Objects.requireNonNull(chunk, "chunk");
    }
}
