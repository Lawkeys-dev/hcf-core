package com.lawkeys.hcfcore.util;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deaths that do not cost what a death usually costs: the King's in Kill the King,
 * today, which takes neither DTR from their team nor a deathban (both configurable,
 * {@code kill-the-king.<id>.reign}).
 *
 * <p>A seam with a neutral default, like the others: nothing is forgiven until a
 * module says so. The module that forgives a death does it before {@code MONITOR}
 * and clears it a tick later; {@code dtr/} and {@code pvp/} read it at
 * {@code MONITOR}, when they charge the death. Main thread only.
 */
public final class ForgivenDeaths {

    /** What a death may be spared. */
    public enum Cost {
        /** The DTR the dead player's team loses. */
        DTR,
        /** The deathban - and so the life a revive would spend on it. */
        DEATHBAN
    }

    private static final Map<UUID, Set<Cost>> FORGIVEN = new ConcurrentHashMap<>();

    private ForgivenDeaths() {
    }

    /** Spares the death this player is dying now the given costs; none is a no-op. */
    public static void forgive(UUID player, Set<Cost> costs) {
        Objects.requireNonNull(player, "player");
        if (!costs.isEmpty()) {
            FORGIVEN.put(player, EnumSet.copyOf(costs));
        }
    }

    /** @return whether the death this player is dying now is spared that cost */
    public static boolean spares(UUID player, Cost cost) {
        Set<Cost> costs = player == null ? null : FORGIVEN.get(player);
        return costs != null && costs.contains(cost);
    }

    /** Once the death is over: the next one is charged as usual. */
    public static void clear(UUID player) {
        FORGIVEN.remove(player);
    }

    /** Nothing forgiven - on shutdown. */
    public static void reset() {
        FORGIVEN.clear();
    }
}
