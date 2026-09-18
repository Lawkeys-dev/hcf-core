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

    /**
     * A hit under Strength, the 1.7.10 way: the modern flat bonus taken back out,
     * then the whole multiplied by {@code 1 + perLevel * level} - Strength I was +130%.
     *
     * @param vanillaBonusPerLevel what the modern game added per level (pvp.yml)
     */
    public static double strength(double damage, int level, double vanillaBonusPerLevel, double perLevel) {
        if (level <= 0) {
            return damage;
        }
        double base = Math.max(0.0, damage - vanillaBonusPerLevel * level);
        return base * (1.0 + perLevel * level);
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
