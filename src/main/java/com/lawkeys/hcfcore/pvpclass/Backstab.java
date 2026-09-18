package com.lawkeys.hcfcore.pvpclass;

import java.util.Objects;

/**
 * The Rogue's backstab: a hit from behind with the right weapon deals a fixed amount
 * of damage that armour, enchantments and effects do not reduce.
 *
 * @param weapon          the item the hit must be dealt with ({@code GOLDEN_SWORD})
 * @param damage          health taken, in half-hearts ({@code 6.0} is three hearts)
 * @param cooldownSeconds wait before the next backstab
 * @param breakWeapon     whether the weapon breaks on a backstab
 * @param maxAngle        how far, in degrees, the attacker may face away from the
 *                        direction the victim faces and still be "behind" them
 */
public record Backstab(String weapon, double damage, long cooldownSeconds, boolean breakWeapon,
                       double maxAngle) {

    public Backstab {
        Objects.requireNonNull(weapon, "weapon");
        if (!(damage > 0)) {
            throw new IllegalArgumentException("damage must be above 0, was " + damage);
        }
        cooldownSeconds = Math.max(0L, cooldownSeconds);
        maxAngle = Math.max(0.0, Math.min(180.0, maxAngle));
    }

    /**
     * Whether a hit comes from behind: the attacker stands behind the victim - on the
     * side the victim turns their back to - and faces roughly the way the victim
     * faces. Both, so a player walking backwards into somebody's face is not behind
     * them, and neither is one standing behind but facing sideways.
     *
     * <p>Horizontal only: looking up or down does not change which way a player's
     * back is turned. Yaws are Minecraft's: 0 faces +Z, 90 faces -X.
     */
    public boolean isBehind(double victimX, double victimZ, float victimYaw,
                            double attackerX, double attackerZ, float attackerYaw) {
        double victimFacingX = -Math.sin(Math.toRadians(victimYaw));
        double victimFacingZ = Math.cos(Math.toRadians(victimYaw));
        double toAttackerX = attackerX - victimX;
        double toAttackerZ = attackerZ - victimZ;
        // The attacker is on the victim's back side: behind the plane the victim faces out of.
        if (victimFacingX * toAttackerX + victimFacingZ * toAttackerZ >= 0) {
            return false;
        }
        return angleBetween(victimYaw, attackerYaw) <= maxAngle;
    }

    /** @return the angle between two yaws, 0 to 180 degrees */
    static double angleBetween(float a, float b) {
        // The signed difference brought into [-180, 180), then its size.
        return Math.abs(((a - b) % 360.0 + 540.0) % 360.0 - 180.0);
    }
}
