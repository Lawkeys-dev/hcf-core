package com.lawkeys.hcfcore.ability;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hits one player lands on another with an item, for the abilities that need several
 * ({@code hits-required}): hitting somebody else, or letting {@code windowMillis}
 * pass between two hits, starts the count over.
 */
public final class HitCounter {

    private record Count(UUID victim, int hits, long lastAt) {
    }

    private final Map<String, Count> counts = new ConcurrentHashMap<>();

    /** @return the hits on this victim so far, this one included */
    public int hit(UUID attacker, String ability, UUID victim, long now, long windowMillis) {
        Objects.requireNonNull(attacker, "attacker");
        Objects.requireNonNull(victim, "victim");
        String key = attacker + ":" + ability;
        Count previous = counts.get(key);
        int hits = previous != null && previous.victim().equals(victim) && now - previous.lastAt() <= windowMillis
                ? previous.hits() + 1 : 1;
        counts.put(key, new Count(victim, hits, now));
        return hits;
    }

    /** The count reached what was needed, or the ability was used: start over. */
    public void reset(UUID attacker, String ability) {
        counts.remove(attacker + ":" + ability);
    }

    public void forget(UUID player) {
        String prefix = player + ":";
        counts.keySet().removeIf(key -> key.startsWith(prefix));
    }
}
