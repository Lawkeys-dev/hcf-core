package com.lawkeys.hcfcore.pvpclass;

import java.util.Locale;
import java.util.Optional;

/** Who an effect a class hands out reaches. */
public enum ClassTarget {

    /** The player using the class, alone. */
    SELF,
    /** The player's teammates in range, the player included; the player alone without a team. */
    TEAM,
    /** The teammates and the members of allied teams in range, the player included. */
    TEAM_AND_ALLIES,
    /**
     * The players in range on neither side - and only those the player could hit:
     * a debuff follows the rules a blow does (safe zones, SOTW, friendly fire).
     */
    ENEMIES;

    /** @return the target a word in the configuration names: {@code self}, {@code team}, {@code team-and-allies}, {@code enemies} */
    public static Optional<ClassTarget> parse(String word) {
        if (word == null) {
            return Optional.empty();
        }
        String normalised = word.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        for (ClassTarget target : values()) {
            if (target.name().equals(normalised)) {
                return Optional.of(target);
            }
        }
        return Optional.empty();
    }

    /** @return the word the configuration uses for it */
    public String configName() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
