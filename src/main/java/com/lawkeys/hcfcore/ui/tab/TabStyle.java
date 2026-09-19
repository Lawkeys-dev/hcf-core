package com.lawkeys.hcfcore.ui.tab;

import com.lawkeys.hcfcore.mode.GameMode;

import java.util.Locale;
import java.util.Optional;

/**
 * Which tab list a server shows (the project owner's request, 19/09/2026): the
 * classic HCF grid, or the player list itself, dressed.
 */
public enum TabStyle {

    /**
     * Four columns of twenty cells written in {@code ui.yml} - player, team, server,
     * top teams - in place of the player list. Needs the PacketEvents plugin: the
     * game has no other way to show a line that is not a player.
     */
    HCF,
    /** The real player list, each name written from a template and sorted. */
    CLASSIC,
    /** The grid on an HCF server, the classic list on a kitmap. */
    AUTO;

    /** @return the style actually shown in this game mode - never {@link #AUTO} */
    public TabStyle resolve(GameMode mode) {
        if (this != AUTO) {
            return this;
        }
        return mode != null && mode.isKitmap() ? CLASSIC : HCF;
    }

    /** @return the style named in a configuration file, case-insensitively */
    public static Optional<TabStyle> parse(String value) {
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
