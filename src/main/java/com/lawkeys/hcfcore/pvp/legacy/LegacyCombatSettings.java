package com.lawkeys.hcfcore.pvp.legacy;

import java.util.List;
import java.util.Objects;

/**
 * The 1.7.10 combat, as {@code pvp.yml}'s {@code legacy-combat} section sets it. It
 * applies only while {@code config.yml} says {@code combat: classic}; in
 * {@code modern}, none of it does.
 *
 * <p>Every value defaults to the 1.7.10 game's, read from how that version played:
 * each is a setting so a server can tune the feel, as HCF servers always did.
 */
public record LegacyCombatSettings(AttackCooldown attackCooldown,
                                   boolean noSweepAttacks,
                                   Criticals criticals,
                                   SwordBlocking swordBlocking,
                                   Knockback knockback,
                                   boolean disableOffhand,
                                   boolean disableShields,
                                   Throw potions,
                                   Pearls pearls,
                                   Regeneration regeneration,
                                   GoldenApples goldenApples,
                                   Strength strength,
                                   FishingRod fishingRod) {

    public LegacyCombatSettings {
        Objects.requireNonNull(attackCooldown, "attackCooldown");
        Objects.requireNonNull(criticals, "criticals");
        Objects.requireNonNull(swordBlocking, "swordBlocking");
        Objects.requireNonNull(knockback, "knockback");
        Objects.requireNonNull(potions, "potions");
        Objects.requireNonNull(pearls, "pearls");
        Objects.requireNonNull(regeneration, "regeneration");
        Objects.requireNonNull(goldenApples, "goldenApples");
        Objects.requireNonNull(strength, "strength");
        Objects.requireNonNull(fishingRod, "fishingRod");
    }

    /** No wait between two full-strength hits: the attack-speed attribute set high enough. */
    public record AttackCooldown(boolean remove, double attackSpeed) {
    }

    /** A critical hit while sprinting too, as 1.7 allowed; the modern game refuses it. */
    public record Criticals(boolean enabled, double multiplier) {
    }

    /**
     * Right-click with a sword to block. 1.7.10 took {@code (damage + 1) / 2}: blocked
     * is {@code base + factor * damage} with base -0.5 and factor 0.5.
     */
    public record SwordBlocking(boolean enabled, double base, double factor) {
    }

    /**
     * 1.7.10 knockback. A hit halves the victim's velocity (divides it by
     * {@code friction}) and pushes it {@code horizontal} away and {@code vertical} up,
     * up to {@code verticalLimit} - whether the victim stands or is in the air, which
     * is what makes air combos. Sprinting or a Knockback enchantment adds
     * {@code extraHorizontal} per level, and {@code extraVertical} up.
     */
    public record Knockback(boolean enabled, double friction, double horizontal, double vertical,
                            double verticalLimit, double extraHorizontal, double extraVertical) {
    }

    /** How a thrown item leaves the hand: 1.7 never added the thrower's own movement. */
    public record Throw(boolean enabled, double speed, double pitchOffset, double inaccuracy) {
    }

    /** Ender pearls: no one-second cooldown, and thrown as 1.7 threw them. */
    public record Pearls(boolean noCooldown, Throw throwing) {
        public Pearls {
            Objects.requireNonNull(throwing, "throwing");
        }
    }

    /**
     * 1.7.10's natural regeneration: {@code amount} every {@code intervalSeconds}
     * while the food bar is at {@code minimumFood} or more, each costing
     * {@code exhaustion} - instead of the modern fast regeneration from saturation.
     */
    public record Regeneration(boolean enabled, double intervalSeconds, double amount, int minimumFood,
                               double exhaustion) {
    }

    /** An effect a golden apple gives. */
    public record AppleEffect(String effect, int level, int seconds) {
        public AppleEffect {
            Objects.requireNonNull(effect, "effect");
        }
    }

    /** What eating an apple does: food, saturation, effects. */
    public record Apple(int food, double saturation, List<AppleEffect> effects) {
        public Apple {
            effects = List.copyOf(effects);
        }
    }

    /** The 1.7.10 golden apple and enchanted (Notch) apple. */
    public record GoldenApples(boolean enabled, Apple golden, Apple enchanted) {
        public GoldenApples {
            Objects.requireNonNull(golden, "golden");
            Objects.requireNonNull(enchanted, "enchanted");
        }
    }

    /** 1.7.10 Strength: the hit multiplied by {@code 1 + perLevel * level}. */
    public record Strength(boolean enabled, double perLevel) {
    }

    /** A fishing rod's hook hitting a player knocks them back and counts as a hit. */
    public record FishingRod(boolean enabled) {
    }

    /** @return every mechanic on, with the 1.7.10 game's values */
    public static LegacyCombatSettings defaults() {
        return new LegacyCombatSettings(
                new AttackCooldown(true, 1024.0),
                true,
                new Criticals(true, 1.5),
                new SwordBlocking(true, -0.5, 0.5),
                new Knockback(true, 2.0, 0.4, 0.4, 0.4, 0.5, 0.1),
                true,
                true,
                new Throw(true, 0.5, -20.0, 1.0),
                new Pearls(true, new Throw(true, 1.5, 0.0, 1.0)),
                new Regeneration(true, 4.0, 1.0, 18, 3.0),
                new GoldenApples(true,
                        new Apple(4, 9.6, List.of(new AppleEffect("regeneration", 2, 5),
                                new AppleEffect("absorption", 1, 120))),
                        new Apple(4, 9.6, List.of(new AppleEffect("regeneration", 5, 30),
                                new AppleEffect("absorption", 1, 120),
                                new AppleEffect("resistance", 1, 300),
                                new AppleEffect("fire_resistance", 1, 300)))),
                new Strength(true, 1.3),
                new FishingRod(true));
    }
}
