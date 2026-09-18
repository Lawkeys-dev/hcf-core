package com.lawkeys.hcfcore.claim;

import java.util.Locale;
import java.util.Optional;

/**
 * The named locations a team can set inside its own territory
 * (FEATURES.md section 2: "HQ" and "Base secondaire").
 */
public enum HomeType {

    /** The team's main base, target of {@code /team hq}. */
    HQ,
    /** A secondary base, target of {@code /team base}. */
    BASE;

    public static Optional<HomeType> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String normalized = id.trim().toUpperCase(Locale.ROOT);
        for (HomeType type : values()) {
            if (type.name().equals(normalized)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
