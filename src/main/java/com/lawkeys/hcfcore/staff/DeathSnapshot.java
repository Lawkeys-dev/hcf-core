package com.lawkeys.hcfcore.staff;

import java.util.Objects;
import java.util.UUID;

/**
 * What a player was carrying when they died.
 *
 * <p>The contents are opaque bytes: turning items into bytes is the server's
 * business, and keeping it out of here is what lets the archive be tested.
 */
public record DeathSnapshot(UUID playerId, long diedAt, byte[] contents) {

    public DeathSnapshot {
        Objects.requireNonNull(playerId, "playerId");
        contents = Objects.requireNonNull(contents, "contents").clone();
    }

    /** @return a copy, so a caller cannot change what the archive holds */
    @Override
    public byte[] contents() {
        return contents.clone();
    }
}
