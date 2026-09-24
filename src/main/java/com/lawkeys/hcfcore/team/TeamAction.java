package com.lawkeys.hcfcore.team;

import java.util.Locale;
import java.util.Optional;

/**
 * Every team operation whose minimum required role is configurable.
 *
 * <p>Server operators disagree about which rank may invite, kick or set a rally,
 * so none of it is hardcoded: {@code teams.yml} maps each action to a
 * {@link TeamRole} and {@link TeamManager} resolves it at call time
 * (ARCHITECTURE.md section 2, "maximum configurability").
 */
public enum TeamAction {

    DISBAND,
    RENAME,
    INVITE,
    REVOKE_INVITE,
    KICK,
    PROMOTE,
    DEMOTE,
    TRANSFER_LEADERSHIP,
    ALLY,
    UNALLY,
    FOCUS,
    RALLY,
    BANK_DEPOSIT,
    BANK_WITHDRAW,
    /** Opening {@code /team settings} to change the team's permissions, members and invitations. */
    SETTINGS;

    /** Config key form, e.g. {@code TRANSFER_LEADERSHIP -> transfer-leadership}. */
    public String configKey() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static Optional<TeamAction> fromConfigKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        String normalized = key.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        for (TeamAction action : values()) {
            if (action.name().equals(normalized)) {
                return Optional.of(action);
            }
        }
        return Optional.empty();
    }
}
