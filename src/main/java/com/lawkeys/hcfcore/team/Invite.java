package com.lawkeys.hcfcore.team;

import java.util.Objects;
import java.util.UUID;

/**
 * A team's invitation to one player. Memory only: invitations reset on restart.
 *
 * @param expiresAt epoch millis, or {@code 0} when invitations never expire
 */
public record Invite(UUID teamId, long expiresAt) {

    public Invite {
        Objects.requireNonNull(teamId, "teamId");
    }

    public boolean isActiveAt(long now) {
        return expiresAt == 0 || now < expiresAt;
    }
}
