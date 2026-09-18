package com.lawkeys.hcfcore.ui;

import java.util.List;
import java.util.Objects;

/**
 * Immutable snapshot of {@code ui.yml}.
 *
 * <p><strong>The scoreboard is a list of template lines, not a fixed layout.</strong>
 * Which numbers an HCF server puts on a player's screen is a matter of taste and of
 * what that server runs - a kitmap has no DTR, a server without events has no KOTH
 * timer - so the lines are the operator's to write, from the placeholders listed in
 * {@code ui.yml}. A line whose placeholders all resolve to nothing is dropped rather
 * than rendered blank, which is what makes a timer line appear only while the timer
 * is running.
 */
public record UiSettings(ScoreboardRules scoreboard, TablistRules tablist) {

    public UiSettings {
        Objects.requireNonNull(scoreboard, "scoreboard");
        Objects.requireNonNull(tablist, "tablist");
    }

    /**
     * @param updateTicks how often the board is redrawn; 20 ticks is once a second,
     *                    which is as fine as any countdown on it needs
     * @param title       the heading, colour codes allowed
     * @param lines       the body, top to bottom
     */
    public record ScoreboardRules(boolean enabled, long updateTicks, String title, List<String> lines) {

        public ScoreboardRules {
            Objects.requireNonNull(title, "title");
            lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
        }
    }

    /** @param header and {@code footer}: the blocks above and below the player list */
    public record TablistRules(boolean enabled, List<String> header, List<String> footer) {

        public TablistRules {
            header = List.copyOf(Objects.requireNonNull(header, "header"));
            footer = List.copyOf(Objects.requireNonNull(footer, "footer"));
        }
    }

    /** Built-in fallback, mirroring {@code resources/ui.yml}. */
    public static UiSettings defaults() {
        return new UiSettings(
                new ScoreboardRules(true, 20L, "&c&lHCF", List.of(
                        "&7&m----------------",
                        "&cTeam: &f%team%",
                        "&cDTR: %dtr_coloured%",
                        "&cKills: &f%kills%",
                        "&cStreak: &f%killstreak%",
                        "&cBalance: &a$%balance%",
                        "%combat_line%",
                        "%pearl_line%",
                        "%phase_line%",
                        "%event_line%",
                        "&7&m----------------")),
                new TablistRules(false, List.of("&c&lHCF"), List.of("&7%online% online")));
    }
}
