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
    COBBLESTONE("cobblestone");

    private final String key;

    PlayerSetting(String key) {
        this.key = key;
    }

    /** @return the name used in commands, configuration and storage */
    public String key() {
        return key;
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
