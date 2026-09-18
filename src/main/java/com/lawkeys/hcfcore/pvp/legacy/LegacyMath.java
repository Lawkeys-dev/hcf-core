package com.lawkeys.hcfcore.pvp.legacy;

import java.util.Objects;

/**
 * The 1.7.10 combat formulas, in plain Java so each is tested against the numbers
 * the old game produced.
 */
public final class LegacyMath {

    private LegacyMath() {
    }

    /** A velocity, in blocks per tick. */
    public record Velocity(double x, double y, double z) {
        public Velocity minus(Velocity other) {
            return new Velocity(x - other.x, y - other.y, z - other.z);
        }
    }

    /**
     * The velocity a hit leaves the victim with, the way 1.7.10 computed it.
     *
     * <p>The victim's velocity is divided by {@code friction}, then it is pushed away
     * from the attacker and lifted - on the ground and in the air alike. The modern
     * game lifts a victim only on the ground (read in the 26.2 sources,
     * {@code LivingEntity#knockback}), which is why combos in the air feel different.
     *
     * @param current      the victim's velocity before the hit
     * @param towardsX     x from the victim to the attacker
     * @param towardsZ     z from the victim to the attacker
     * @param resistance   the victim's knockback resistance, 0 to 1
     */
    public static Velocity knockback(Velocity current, double towardsX, double towardsZ, double resistance,
                                     LegacyCombatSettings.Knockback rules) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(rules, "rules");
        double keep = 1.0 - Math.max(0.0, Math.min(1.0, resistance));
        double horizontal = rules.horizontal() * keep;
        double vertical = rules.vertical() * keep;
        double length = Math.sqrt(towardsX * towardsX + towardsZ * towardsZ);
        double dx = length < 1.0E-4 ? 0.0 : towardsX / length;
        double dz = length < 1.0E-4 ? 0.0 : towardsZ / length;
        double friction = rules.friction() > 0 ? rules.friction() : 1.0;
        double y = current.y() / friction + vertical;
        return new Velocity(
                current.x() / friction - dx * horizontal,
                Math.min(rules.verticalLimit(), y),
                current.z() / friction - dz * horizontal);
    }

    /**
     * The extra push of a sprint hit or a Knockback enchantment, the 1.7.10 way:
     * added on top, in the direction the attacker faces, with a small lift.
     *
     * @param attackerYaw Minecraft's yaw, in degrees
     * @param level       sprinting counts 1, plus the Knockback enchantment's level
     */
    public static Velocity extraKnockback(float attackerYaw, int level, LegacyCombatSettings.Knockback rules) {
        Objects.requireNonNull(rules, "rules");
        if (level <= 0) {
            return new Velocity(0, 0, 0);
        }
        double yaw = Math.toRadians(attackerYaw);
        return new Velocity(-Math.sin(yaw) * level * rules.extraHorizontal(), rules.extraVertical(),
                Math.cos(yaw) * level * rules.extraHorizontal());
    }

    /**
     * Whether a hit is a critical one by 1.7.10's rules: falling, in the air, not on a
     * ladder or in water, not blind, not riding. The modern game adds two more - a
     * charged attack, and not sprinting - so this is what lets a sprint hit crit.
     */
    public static boolean isCritical(double fallDistance, boolean onGround, boolean climbing, boolean inWater,
                                     boolean blind, boolean riding) {
        return fallDistance > 0 && !onGround && !climbing && !inWater && !blind && !riding;
    }

    /** The modern critical hit's multiplier (26.2, {@code Player#attack}: {@code 1.5f}). */
    public static final double MODERN_CRITICAL = 1.5;

    /**
     * The modern Sharpness bonus: 1 at level I, 0.5 per level above
     * (26.2, {@code data/minecraft/enchantment/sharpness.json}).
     */
    public static double modernSharpness(int level) {
        return level <= 0 ? 0.0 : 1.0 + 0.5 * (level - 1);
    }

    /**
     * How Strength counts in a rebuilt hit: 1.7's multiplier of the weapon's damage
     * ({@code multiply}), or a flat bonus per level - the modern game's, or the HCF
     * nerf's.
     */
    public record StrengthRule(boolean multiply, double perLevel) {
    }

    /**
     * A melee hit rebuilt the 1.7.10 way from the one the modern game computed.
     *
     * <p>The modern game deals {@code (attack + vanilla Strength) x critical +
     * enchantment} (26.2, {@code Player#attack}: the critical multiplies the attack
     * alone, the enchantment's bonus is added after it); with no attack cooldown the
     * attack-strength scale is 1. That is taken apart, then put back together as 1.7
     * did: the weapon, Strength by {@code strength}, the critical by
     * {@code critical}, and Sharpness last - 1.7's {@code sharpnessPerLevel} per
     * level, or the modern bonus when empty.
     *
     * @param damage             the hit the modern game computed
     * @param modernCritical     whether the modern game counted it critical
     * @param vanillaStrength    what the modern game adds per Strength level (pvp.yml)
     * @param sharpnessPerLevel  1.7's Sharpness per level, or empty to keep the modern bonus
     */
    public static double rebuildHit(double damage, boolean modernCritical, int strengthLevel,
                                    double vanillaStrength, StrengthRule strength,
                                    boolean critical, double criticalMultiplier,
                                    int sharpness, java.util.OptionalDouble sharpnessPerLevel) {
        double modernEnchant = modernSharpness(sharpness);
        double attack = Math.max(0.0, (damage - modernEnchant) / (modernCritical ? MODERN_CRITICAL : 1.0));
        int level = Math.max(0, strengthLevel);
        double weapon = Math.max(0.0, attack - vanillaStrength * level);
        double hit = strength.multiply() ? weapon * (1.0 + strength.perLevel() * level)
                : weapon + strength.perLevel() * level;
        if (critical) {
            hit *= criticalMultiplier;
        }
        double enchant = sharpnessPerLevel.isPresent() ? sharpnessPerLevel.getAsDouble() * Math.max(0, sharpness)
                : modernEnchant;
        return hit + enchant;
    }

    /**
     * How fast a thrown item leaves the hand, the way 1.7.10 threw it: in the
     * direction the thrower looks, lifted by {@code pitchOffset}, at {@code speed},
     * spread by {@code inaccuracy} - and without the thrower's own movement, which the
     * modern game adds.
     *
     * @param gaussians three draws from a standard normal distribution, for the spread
     */
    public static Velocity throwVelocity(float yaw, float pitch, LegacyCombatSettings.Throw rules,
                                         double[] gaussians) {
        Objects.requireNonNull(rules, "rules");
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double x = -Math.sin(yawRad) * Math.cos(pitchRad);
        double y = -Math.sin(Math.toRadians(pitch + rules.pitchOffset()));
        double z = Math.cos(yawRad) * Math.cos(pitchRad);
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length < 1.0E-9) {
            return new Velocity(0, 0, 0);
        }
        double spread = 0.0075 * rules.inaccuracy();
        x = x / length + gaussians[0] * spread;
        y = y / length + gaussians[1] * spread;
        z = z / length + gaussians[2] * spread;
        return new Velocity(x * rules.speed(), y * rules.speed(), z * rules.speed());
    }
}
