package com.lawkeys.hcfcore.limiter;

import com.lawkeys.hcfcore.util.ChunkPosition;

import java.util.Objects;
import java.util.UUID;

/**
 * The part of one chunk one team owns: what limited blocks are counted by.
 *
 * <p>Claims are block-precise since 22/09/2026, so one chunk can hold two teams' land
 * - two bases a buffer apart, a base and a road. Counted by chunk alone, each would
 * have been charged the other's hoppers.
 */
public record ClaimCell(ChunkPosition chunk, UUID teamId) {

    public ClaimCell {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(teamId, "teamId");
    }
}
