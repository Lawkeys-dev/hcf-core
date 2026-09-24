package com.lawkeys.hcfcore.team;

import java.util.Locale;
import java.util.Optional;

/** Who may join a team: nobody, the invited (as shipped), or anybody. */
public enum JoinMode {

    /** Nobody: no invitation is sent or accepted - only staff put a player in. */
    CLOSED,
    /** The invited, as HCF always had it. */
    INVITE,
    /** Anybody, with {@code /team join}. */
    OPEN;

    /** The form used in {@code teams.yml}, the database and the language file. */
    public String configKey() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<JoinMode> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String normalized = id.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (normalized.equals("INVITE_ONLY") || normalized.equals("INVITATION")) {
            return Optional.of(INVITE);
        }
        for (JoinMode mode : values()) {
            if (mode.name().equals(normalized)) {
                return Optional.of(mode);
            }
        }
        return Optional.empty();
    }
}
