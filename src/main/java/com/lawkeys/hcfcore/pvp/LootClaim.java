package com.lawkeys.hcfcore.pvp;

import java.util.Objects;
import java.util.UUID;

/**
 * Anticlean (FEATURES.md section 12): who a dead player's loot belongs to, and
 * until when.
 *
 * <p>For a few seconds after a kill, what the victim drops can be picked up only by
 * the killer - and by the killer's team, when {@code team} is set. Nobody else, no
 * mob, no hopper. Afterwards it is ordinary loot. The rule the project owner chose on
 * 12/09/2026, over protecting the winner from third parties.
 *
 * @param killer who the loot belongs to
 * @param team   the killer's team at the time of the kill, sharing the loot; or
 *               {@code null} when it is the killer's alone
 * @param until  epoch millis at which the loot becomes anybody's
 */
public record LootClaim(UUID killer, UUID team, long until) {

    public LootClaim {
        Objects.requireNonNull(killer, "killer");
    }

    public boolean isActive(long now) {
        return now < until;
    }

    /**
     * @param picker     who is picking it up, or {@code null} for a mob or a hopper
     * @param pickerTeam the picker's team now, or {@code null}
     */
    public boolean mayPickUp(UUID picker, UUID pickerTeam, long now) {
        if (!isActive(now)) {
            return true;
        }
        if (picker == null) {
            return false;
        }
        return picker.equals(killer) || (team != null && team.equals(pickerTeam));
    }

    /**
     * Whether two dropped stacks may merge into one: a merge keeps only one of the
     * two claims, so loot under a claim merges only with loot under the same one.
     *
     * @param a the claim on one stack, or {@code null} for none
     * @param b the claim on the other, or {@code null} for none
     */
    public static boolean mayMerge(LootClaim a, LootClaim b, long now) {
        boolean aActive = a != null && a.isActive(now);
        boolean bActive = b != null && b.isActive(now);
        if (!aActive && !bActive) {
            return true;
        }
        return aActive && bActive && a.killer().equals(b.killer()) && Objects.equals(a.team(), b.team());
    }
}
