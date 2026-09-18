package com.lawkeys.hcfcore.claim;

import com.lawkeys.hcfcore.util.WorldPosition;

import java.util.Objects;
import java.util.UUID;

/**
 * A team's HQ or secondary base.
 *
 * <p>Owned by the claim module rather than by {@code Team}: homes only mean
 * something in relation to territory, and keeping them here lets the claim
 * module version its own schema without touching the team tables
 * (ARCHITECTURE.md section 4).
 */
public record TeamHome(UUID teamId, HomeType type, WorldPosition position, long setAt) {

    public TeamHome {
        Objects.requireNonNull(teamId, "teamId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(position, "position");
    }
}
