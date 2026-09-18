package com.lawkeys.hcfcore.crowbar;

import java.util.Objects;
import java.util.UUID;

/**
 * Where a crowbar may take an End portal frame out (FEATURES.md section 17).
 *
 * <p>Pure Java. The rule the project owner set is strict and has no exception: in
 * the wilderness, or in your own team's claim - <strong>never in an enemy
 * claim</strong>, raidable or not. The crowbar is there so a team can adjust or take
 * out its own traps, not so a raid can defuse somebody else's. The warzone and
 * server land (spawn, roads) are neither wilderness nor yours.
 */
public final class CrowbarRules {

    private CrowbarRules() {
    }

    /** Where the frame stands, from the crowbar's point of view. */
    public enum Verdict {
        ALLOWED,
        /** Somebody else's claim - raidable makes no difference. */
        ENEMY_CLAIM,
        /** A server team's land: spawn, a road, an event ground. */
        SERVER_LAND,
        /** The warzone, which no team owns but nobody builds in either. */
        WARZONE
    }

    /**
     * @param owner         the team owning the chunk, or {@code null} for nobody
     * @param ownerIsServer whether that team is a server (system) team
     * @param warzone       whether the chunk is in the warzone
     * @param playerTeam    the player's own team, or {@code null} if they have none
     */
    public static Verdict judge(UUID owner, boolean ownerIsServer, boolean warzone, UUID playerTeam) {
        if (owner == null) {
            return warzone ? Verdict.WARZONE : Verdict.ALLOWED;
        }
        if (ownerIsServer) {
            return Verdict.SERVER_LAND;
        }
        return Objects.equals(owner, playerTeam) ? Verdict.ALLOWED : Verdict.ENEMY_CLAIM;
    }

    /**
     * @param uses the uses left before this one; {@code 0} means the crowbar has no limit
     * @return the uses left after it, where {@code 0} means the crowbar is spent - or
     *         {@link Integer#MAX_VALUE} for one without a limit
     */
    public static int afterUse(int uses) {
        return uses <= 0 ? Integer.MAX_VALUE : uses - 1;
    }
}
