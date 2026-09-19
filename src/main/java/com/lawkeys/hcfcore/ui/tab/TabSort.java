package com.lawkeys.hcfcore.ui.tab;

import java.util.Locale;
import java.util.Optional;

/** How the classic tab list orders players; names break ties, as the game does. */
public enum TabSort {

    /** The weight of the player's LuckPerms primary group, heaviest first. */
    RANK,
    /** Most kills first. */
    KILLS,
    /** By name alone - the game's own order. */
    NAME;

    public static Optional<TabSort> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
