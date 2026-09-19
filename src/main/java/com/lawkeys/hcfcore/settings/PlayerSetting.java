package com.lawkeys.hcfcore.settings;

import java.util.Locale;
import java.util.Optional;

/**
 * What a player can switch off for themselves.
 *
 * <p>Each one switches off something the plugin actually does. FEATURES.md section 9
 * also mentions sounds; the plugin plays none, so a sound toggle would be a switch
 * connected to nothing - the ghost behaviour ARCHITECTURE.md section 2 forbids.
 */
public enum PlayerSetting {

    /** The HCF scoreboard. */
    SCOREBOARD("scoreboard"),
    /** Private messages, both ways - the same switch as {@code /togglepm}. */
    PRIVATE_MESSAGES("private-messages"),
    /** The rotating tips. */
    TIPS("tips"),
    /** Picking up cobblestone - the HCF {@code /cobble}, for mining without filling up on it. */
    COBBLESTONE("cobblestone"),
    /**
     * The scoreboard's rows, section by section: a row of {@code ui.yml} starting
     * with {@code [team]} is hidden when {@code scoreboard-team} is off - and so on.
     */
    SCOREBOARD_TEAM("scoreboard-team"),
    SCOREBOARD_STATS("scoreboard-stats"),
    SCOREBOARD_BALANCE("scoreboard-balance"),
    SCOREBOARD_COMBAT("scoreboard-combat"),
    SCOREBOARD_COOLDOWNS("scoreboard-cooldowns"),
    SCOREBOARD_CLASS("scoreboard-class"),
    SCOREBOARD_EVENTS("scoreboard-events"),
    SCOREBOARD_TIMERS("scoreboard-timers");

    /** What starts the key of a scoreboard section's setting. */
    public static final String SECTION_PREFIX = "scoreboard-";

    private final String key;

    PlayerSetting(String key) {
        this.key = key;
    }

    /** @return the name used in commands, configuration and storage */
    public String key() {
        return key;
    }

    /** @return the setting that hides the scoreboard rows tagged {@code [section]}, if there is one */
    public static Optional<PlayerSetting> scoreboardSection(String section) {
        return section == null || section.isBlank() ? Optional.empty() : byKey(SECTION_PREFIX + section);
    }

    public static Optional<PlayerSetting> byKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        String wanted = key.trim().toLowerCase(Locale.ROOT);
        for (PlayerSetting setting : values()) {
            if (setting.key.equals(wanted)) {
                return Optional.of(setting);
            }
        }
        return Optional.empty();
    }
}
