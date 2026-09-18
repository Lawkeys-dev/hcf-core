package com.lawkeys.hcfcore.pvp;

import java.util.Objects;

/**
 * The arithmetic behind the combat tweaks, kept out of the listeners so it can be
 * unit-tested without a server.
 *
 * <p>Both operations here are deliberately expressed as adjustments driven by
 * configuration rather than as reimplementations of a vanilla formula - see
 * {@link PvpSettings.StrengthRules} for why the vanilla figure is a config value
 * and not a constant.
 */
public final class CombatMath {

    private CombatMath() {
    }

    /**
     * Applies the historic HCF strength nerf to one hit.
     *
     * <p>Vanilla has already added its own bonus by the time a damage event is
     * seen, so the nerf works by subtracting what vanilla granted and adding the
     * configured amount instead.
     *
     * @param rawDamage        damage as the server computed it, vanilla bonus included
     * @param strengthLevel    the attacker's Strength level, {@code 0} for none
     *                         (amplifier 0 is level 1)
     * @return the adjusted damage, never negative
     */
    public static double nerfStrength(double rawDamage, int strengthLevel,
                                      PvpSettings.StrengthRules rules) {
        Objects.requireNonNull(rules, "rules");
        if (!rules.enabled() || strengthLevel <= 0) {
            return rawDamage;
        }
        double vanilla = rules.vanillaBonusPerLevel() * strengthLevel;
        double nerfed = rules.nerfedBonusPerLevel() * strengthLevel;
        return Math.max(0.0, rawDamage - vanilla + nerfed);
    }

    /**
     * Scales a knockback vector.
     *
     * @param components the x, y and z the server was about to apply
     * @return the scaled components, or the input untouched when tuning is off
     */
    public static double[] scaleKnockback(double[] components, PvpSettings.KnockbackRules rules) {
        Objects.requireNonNull(components, "components");
        Objects.requireNonNull(rules, "rules");
        if (components.length != 3) {
            throw new IllegalArgumentException("expected an x/y/z vector, got " + components.length);
        }
        if (!rules.enabled()) {
            return components;
        }
        return new double[] {
                components[0] * rules.horizontal(),
                components[1] * rules.vertical(),
                components[2] * rules.horizontal(),
        };
    }

    /**
     * The amount the attack-speed modifier has to add for a player's attribute to
     * start from the configured value instead of its own base.
     *
     * <p>A difference rather than a new base value, because the base value is saved
     * with the player: a modifier that is never saved leaves nothing behind when the
     * setting is turned off or the plugin removed.
     *
     * @param base the player's own base value
     * @return the amount to add; {@code 0} means no modifier is needed
     */
    public static double attackSpeedModifier(double base, PvpSettings.AttackSpeedRules rules) {
        Objects.requireNonNull(rules, "rules");
        return rules.enabled() ? rules.value() - base : 0.0;
    }
}
