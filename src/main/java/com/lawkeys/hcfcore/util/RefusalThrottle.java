package com.lawkeys.hcfcore.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * At most one refusal message per player per interval.
 *
 * <p>What a player is refused often repeats by itself: holding a mouse button makes
 * the client repeat the click every four ticks - five times a second - for as long
 * as nothing is being used, and pushing against a border fires a move every tick.
 * Each repeat is refused, as it should be; telling the player each time filled the
 * chat (found in game, 14/09/2026). The first refusal is always explained.
 *
 * <p>Used for territory protection, locked claims, resource nodes and frozen players.
 * Main thread only, like the listeners that ask it.
 */
public final class RefusalThrottle {

    private final long intervalMillis;
    private final Map<UUID, Long> lastTold = new HashMap<>();

    public RefusalThrottle(long intervalMillis) {
        if (intervalMillis < 0) {
            throw new IllegalArgumentException("intervalMillis must not be negative: " + intervalMillis);
        }
        this.intervalMillis = intervalMillis;
    }

    /**
     * @return whether to tell the player now; when it does, the interval starts
     *         again from {@code now}
     */
    public boolean tryTell(UUID playerId, long now) {
        Objects.requireNonNull(playerId, "playerId");
        Long last = lastTold.get(playerId);
        if (last != null && now - last < intervalMillis) {
            return false;
        }
        lastTold.put(playerId, now);
        return true;
    }

    public void forget(UUID playerId) {
        lastTold.remove(playerId);
    }
}
