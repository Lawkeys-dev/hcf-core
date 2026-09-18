package com.lawkeys.hcfcore.team;

/**
 * Distinguishes ordinary player teams from server-owned "system" teams
 * (spawn, safe zones, warzone, event areas) - see FEATURES.md section 1,
 * "System team".
 *
 * <p>System teams own territory but have no members, no leader and no DTR; they
 * exist so that the claim module can attribute every protected chunk to a team
 * without a special case for server-owned land.
 */
public enum TeamType {

    PLAYER,
    SYSTEM;

    public boolean isSystem() {
        return this == SYSTEM;
    }
}
