package com.lawkeys.hcfcore.team;

import java.util.Locale;
import java.util.Optional;

/**
 * Hierarchical role of a player inside a team (see FEATURES.md section 1).
 *
 * <p>Ordering is expressed through {@link #weight()} rather than the enum's own
 * ordinal so that a role can later be inserted in the middle of the hierarchy
 * without silently changing the meaning of persisted data.
 */
public enum TeamRole {

    MEMBER(0),
    CO_LEADER(1),
    LEADER(2);

    private final int weight;

    TeamRole(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return weight;
    }

    /** @return {@code true} if this role is at least as high in the hierarchy as {@code other}. */
    public boolean isAtLeast(TeamRole other) {
        return this.weight >= other.weight;
    }

    /** @return {@code true} if this role is strictly above {@code other} in the hierarchy. */
    public boolean outranks(TeamRole other) {
        return this.weight > other.weight;
    }

    /** @return the next role up, or empty if already at the top. */
    public Optional<TeamRole> promoted() {
        return switch (this) {
            case MEMBER -> Optional.of(CO_LEADER);
            case CO_LEADER -> Optional.of(LEADER);
            case LEADER -> Optional.empty();
        };
    }

    /** @return the next role down, or empty if already at the bottom. */
    public Optional<TeamRole> demoted() {
        return switch (this) {
            case LEADER -> Optional.of(CO_LEADER);
            case CO_LEADER -> Optional.of(MEMBER);
            case MEMBER -> Optional.empty();
        };
    }

    /**
     * Parses a role from a configuration or database value, case-insensitively and
     * tolerating the {@code co-leader} spelling used in config files.
     */
    public static Optional<TeamRole> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String normalized = id.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        for (TeamRole role : values()) {
            if (role.name().equals(normalized)) {
                return Optional.of(role);
            }
        }
        return Optional.empty();
    }
}
