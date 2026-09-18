package com.lawkeys.hcfcore.pvp.legacy;

import java.util.Locale;
import java.util.Optional;

/** Which combat the server plays, set by {@code combat:} in {@code config.yml}. */
public enum CombatMode {

    /** The game's own combat, as it comes: attack cooldown, sweeping, shields. */
    MODERN,
    /** The 1.7.10 feel HCF grew up on, tuned in {@code pvp.yml}'s {@code legacy-combat}. */
    CLASSIC;

    /** @return the mode a word names, {@code modern} or {@code classic} */
    public static Optional<CombatMode> parse(String word) {
        if (word == null) {
            return Optional.empty();
        }
        String normalised = word.trim().toUpperCase(Locale.ROOT);
        for (CombatMode mode : values()) {
            if (mode.name().equals(normalised)) {
                return Optional.of(mode);
            }
        }
        return Optional.empty();
    }
}
