package com.lawkeys.hcfcore.pvp;

import com.lawkeys.hcfcore.util.Durations;

import java.util.Objects;
import java.util.UUID;

/**
 * A player's active deathban.
 *
 * <p>Stored as an expiry instant rather than a remaining duration, so it keeps
 * counting down while the server is off - a deathban that paused on restart would
 * be trivially farmable.
 */
public record Deathban(UUID playerId, long expiresAt, String reason) {

    /**
     * The expiry of a ban that lasts until the map ends (EOTW). Never reached, so
     * the ban is never purged; staff lift it after the reset.
     */
    public static final long UNTIL_MAP_END = Long.MAX_VALUE;

    public Deathban {
        Objects.requireNonNull(playerId, "playerId");
    }

    /** @return whether this ban lasts until the map ends rather than for a time */
    public boolean isUntilMapEnd() {
        return expiresAt == UNTIL_MAP_END;
    }

    public boolean isActiveAt(long now) {
        return now < expiresAt;
    }

    /** @return whole seconds left at {@code now}, never negative. */
    public long remainingSeconds(long now) {
        long remaining = expiresAt - now;
        return Durations.secondsLeft(remaining);
    }
}
