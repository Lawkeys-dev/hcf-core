package com.lawkeys.hcfcore.util;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player waits between two uses of something: a partner item, a crowbar, the
 * Recover enchant, a refill sign. Each module keeps its own instance, so its keys
 * are its own.
 *
 * <p>Memory only. These waits are seconds, not hours - they exist so an item cannot
 * be spammed - and none of them needs to survive a restart. Longer waits that must
 * (kit cooldowns) are stored by their module. Other expirations - a combat tag, an
 * invitation - have rules of their own and do not use this.
 */
public final class Cooldowns {

    private final Map<UUID, Map<String, Long>> until = new ConcurrentHashMap<>();

    /** @return whole seconds still to wait, rounded up, or {@code 0} */
    public long remaining(UUID playerId, String key, long now) {
        Long expiry = until.getOrDefault(playerId, Map.of()).get(key);
        return expiry == null ? 0L : Durations.secondsLeft(expiry - now);
    }

    /** @return whether the player must still wait before using {@code key} again */
    public boolean isWaiting(UUID playerId, String key, long now) {
        return remaining(playerId, key, now) > 0;
    }

    /** Starts a wait of {@code seconds}, replacing any running one; {@code 0} or less records nothing. */
    public void start(UUID playerId, String key, long seconds, long now) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(key, "key");
        if (seconds > 0) {
            until.computeIfAbsent(playerId, id -> new ConcurrentHashMap<>()).put(key, now + seconds * 1000L);
        }
    }

    /**
     * Starts the wait if there is none.
     *
     * @return {@code false}, changing nothing, if the player is still waiting - so the
     *         caller refuses the use, and hammering the item cannot extend the wait
     */
    public boolean tryUse(UUID playerId, String key, long seconds, long now) {
        if (isWaiting(playerId, key, now)) {
            return false;
        }
        start(playerId, key, seconds, now);
        return true;
    }

    /** Ends one wait of a player's at once. */
    public void clear(UUID playerId, String key) {
        Map<String, Long> waits = until.get(playerId);
        if (waits != null) {
            waits.remove(key);
        }
    }

    public void forget(UUID playerId) {
        until.remove(playerId);
    }

    public void clearAll() {
        until.clear();
    }
}
