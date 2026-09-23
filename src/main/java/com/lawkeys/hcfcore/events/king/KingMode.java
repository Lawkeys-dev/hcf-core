package com.lawkeys.hcfcore.events.king;

import java.util.Locale;
import java.util.Optional;

/**
 * How a Kill the King is fought (the project owner's choice, 23/09/2026).
 */
public enum KingMode {

    /**
     * The King's team defends them against every other player. The winner's team
     * scores {@code points.per-king-win}; the reward commands run for the winner.
     */
    TEAM,

    /**
     * Everybody against the King - their own teammates and allies included, and the
     * King may strike anybody back. No team scores: the reward is the winner's own,
     * the reward commands (crate keys, money...).
     */
    SOLO;

    public static Optional<KingMode> of(String raw) {
        String typed = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        for (KingMode mode : values()) {
            if (mode.name().equals(typed)) {
                return Optional.of(mode);
            }
        }
        return Optional.empty();
    }
}
