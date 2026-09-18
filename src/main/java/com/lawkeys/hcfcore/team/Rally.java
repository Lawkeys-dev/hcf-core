package com.lawkeys.hcfcore.team;

import com.lawkeys.hcfcore.util.WorldPosition;

import java.util.Objects;

/**
 * A team's rally point.
 *
 * @param expiresAt epoch millis, or {@code 0} when the rally lasts until cleared
 */
public record Rally(WorldPosition position, long expiresAt) {

    public Rally {
        Objects.requireNonNull(position, "position");
    }

    public boolean isActiveAt(long now) {
        return expiresAt == 0 || now < expiresAt;
    }
}
