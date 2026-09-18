package com.lawkeys.hcfcore.pvp.legacy;

import com.lawkeys.hcfcore.pvp.legacy.LegacyMath.Velocity;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The 1.7.10 combat formulas, checked against what the old game did. */
class LegacyCombatTest {

    private static final LegacyCombatSettings SETTINGS = LegacyCombatSettings.defaults();
    private static final double EPSILON = 1e-9;

    @Nested
    class Knockback {

        private final LegacyCombatSettings.Knockback rules = SETTINGS.knockback();

        @Test
        void aStandingVictimIsPushedAwayAndLifted() {
            // The attacker stands on the victim's -X side: the push goes towards +X.
            Velocity after = LegacyMath.knockback(new Velocity(0, -0.0784, 0), -1, 0, 0, rules);
            assertEquals(0.4, after.x(), EPSILON);
            assertEquals(-0.0392 + 0.4, after.y(), EPSILON);
            assertEquals(0.0, after.z(), EPSILON);
        }

        @Test
        void aVictimInTheAirIsLiftedToo() {
            // The modern game leaves the vertical velocity of a victim in the air untouched;
            // 1.7 lifts them all the same - the combo.
            Velocity after = LegacyMath.knockback(new Velocity(0, -0.2, 0), -1, 0, 0, rules);
            assertEquals(-0.1 + 0.4, after.y(), EPSILON);
        }

        @Test
        void theLiftIsCapped() {
            Velocity after = LegacyMath.knockback(new Velocity(0, 0.3, 0), -1, 0, 0, rules);
            assertEquals(0.4, after.y(), EPSILON, "0.15 + 0.4 capped at vertical-limit");
        }

        @Test
        void theVelocityIsHalvedBeforeThePush() {
            Velocity after = LegacyMath.knockback(new Velocity(0.6, 0, -0.2), 0, 1, 0, rules);
            assertEquals(0.3, after.x(), EPSILON);
            assertEquals(-0.1 - 0.4, after.z(), EPSILON, "pushed away from an attacker on the +Z side");
        }

        @Test
        void knockbackResistanceReducesThePush() {
            Velocity after = LegacyMath.knockback(new Velocity(0, 0, 0), -1, 0, 0.5, rules);
            assertEquals(0.2, after.x(), EPSILON);
            assertEquals(0.2, after.y(), EPSILON);
        }

        @Test
        void theDirectionIsNormalised() {
            Velocity after = LegacyMath.knockback(new Velocity(0, 0, 0), -3, -4, 0, rules);
            assertEquals(0.4 * 0.6, after.x(), EPSILON);
            assertEquals(0.4 * 0.8, after.z(), EPSILON);
        }

        @Test
        void aSprintHitAddsAPushWhereTheAttackerFaces() {
            // Yaw 0 faces +Z.
            Velocity extra = LegacyMath.extraKnockback(0f, 1, rules);
            assertEquals(0.0, extra.x(), EPSILON);
            assertEquals(0.1, extra.y(), EPSILON);
            assertEquals(0.5, extra.z(), EPSILON);
            Velocity twice = LegacyMath.extraKnockback(90f, 2, rules);
            assertEquals(-1.0, twice.x(), EPSILON, "yaw 90 faces -X; level 2 doubles it");
        }

        @Test
        void noLevelNoExtraPush() {
            assertEquals(new Velocity(0, 0, 0), LegacyMath.extraKnockback(0f, 0, rules));
        }
    }

    @Nested
    class Criticals {

        @Test
        void fallingInTheAirIsCriticalWhateverTheSprint() {
            assertTrue(LegacyMath.isCritical(0.5, false, false, false, false, false));
        }

        @Test
        void standingClimbingSwimmingBlindOrRidingIsNot() {
            assertFalse(LegacyMath.isCritical(0.0, false, false, false, false, false), "not falling");
            assertFalse(LegacyMath.isCritical(0.5, true, false, false, false, false), "on the ground");
            assertFalse(LegacyMath.isCritical(0.5, false, true, false, false, false), "on a ladder");
            assertFalse(LegacyMath.isCritical(0.5, false, false, true, false, false), "in water");
            assertFalse(LegacyMath.isCritical(0.5, false, false, false, true, false), "blind");
            assertFalse(LegacyMath.isCritical(0.5, false, false, false, false, true), "riding");
        }
    }

    @Nested
    class Hits {

        private static final LegacyMath.StrengthRule OLD_STRENGTH = new LegacyMath.StrengthRule(true, 1.3);
        private static final java.util.OptionalDouble OLD_SHARPNESS = java.util.OptionalDouble.of(1.25);
        private static final java.util.OptionalDouble MODERN_SHARPNESS = java.util.OptionalDouble.empty();

        /** A blow rebuilt with 1.7 Strength and Sharpness, a critical multiplying by 1.5. */
        private double rebuild(double damage, boolean modernCritical, int strength, boolean critical, int sharpness) {
            return LegacyMath.rebuildHit(damage, modernCritical, strength, 3.0, OLD_STRENGTH, critical, 1.5,
                    sharpness, OLD_SHARPNESS);
        }

        @Test
        void strengthOneIsPlusOneHundredAndThirtyPercent() {
            // A 10-damage hit under the modern Strength I (+3): 7 without it, times 2.3.
            assertEquals(16.1, rebuild(10, false, 1, false, 0), EPSILON);
        }

        @Test
        void strengthTwoIsPlusTwoHundredAndSixtyPercent() {
            assertEquals((13 - 6) * 3.6, rebuild(13, false, 2, false, 0), EPSILON);
        }

        @Test
        void aPlainHitIsLeftAsItIs() {
            assertEquals(7.0, rebuild(7, false, 0, false, 0), EPSILON);
        }

        @Test
        void theModernSharpnessIsOneAndAHalfPerLevelAbove() {
            assertEquals(0.0, LegacyMath.modernSharpness(0));
            assertEquals(1.0, LegacyMath.modernSharpness(1));
            assertEquals(3.0, LegacyMath.modernSharpness(5));
        }

        @Test
        void sharpnessFiveIsSixAndAQuarter() {
            // A diamond sword at 8 with the modern Sharpness V (+3): 8 + 1.25 x 5.
            assertEquals(14.25, rebuild(11, false, 0, false, 5), EPSILON);
        }

        @Test
        void aCriticalNeverMultipliesSharpness() {
            // Modern: 8 x 1.5 + 3. The old game: 8 x 1.5 + 6.25.
            assertEquals(18.25, rebuild(15, true, 0, true, 5), EPSILON);
        }

        @Test
        void aSprintCriticalMultipliesTheWeaponOnly() {
            // Not critical for the modern game (sprinting), critical by 1.7's rules.
            assertEquals(18.25, rebuild(11, false, 0, true, 5), EPSILON);
        }

        @Test
        void strengthCriticalAndSharpnessTogether() {
            // Modern: (8 + 3) x 1.5 + 3 = 19.5. The old game: 8 x 2.3 x 1.5 + 6.25.
            assertEquals(8 * 2.3 * 1.5 + 6.25, rebuild(19.5, true, 1, true, 5), EPSILON);
        }

        @Test
        void sharpnessSwitchedOffKeepsTheModernBonus() {
            assertEquals(11.0, LegacyMath.rebuildHit(11, false, 0, 3.0, OLD_STRENGTH, false, 1.5, 5,
                    MODERN_SHARPNESS), EPSILON);
        }

        @Test
        void theClassicNerfIsAPercentageToo() {
            var nerfed = new LegacyCombatSettings.Strength(true, 1.3, true, 0.65);
            var unnerfed = new LegacyCombatSettings.Strength(true, 1.3, false, 0.65);
            assertEquals(0.65, nerfed.effectivePerLevel());
            assertEquals(1.3, unnerfed.effectivePerLevel());
            // A diamond sword at 8 under the modern Strength I (+3): 8 x 1.65.
            assertEquals(8 * 1.65, LegacyMath.rebuildHit(11, false, 1, 3.0,
                    new LegacyMath.StrengthRule(true, nerfed.effectivePerLevel()), false, 1.5, 0, OLD_SHARPNESS),
                    EPSILON);
        }

        @Test
        void theNerfAddsItsFlatBonusInsteadOfTheModernOne() {
            // 8 + the modern Strength I (+3); nerfed to +1.5.
            assertEquals(9.5, LegacyMath.rebuildHit(11, false, 1, 3.0, new LegacyMath.StrengthRule(false, 1.5),
                    false, 1.5, 0, OLD_SHARPNESS), EPSILON);
        }
    }

    @Nested
    class Throws {

        private final double[] noSpread = {0, 0, 0};

        @Test
        void aPotionLeavesAtItsSpeedLiftedByItsPitchOffset() {
            Velocity potion = LegacyMath.throwVelocity(0f, 0f, SETTINGS.potions(), noSpread);
            double speed = Math.sqrt(potion.x() * potion.x() + potion.y() * potion.y() + potion.z() * potion.z());
            assertEquals(0.5, speed, 1e-6);
            assertTrue(potion.y() > 0, "lifted 20 degrees above where the player looks");
            assertTrue(potion.z() > 0, "yaw 0 throws towards +Z");
            assertEquals(0.0, potion.x(), EPSILON);
        }

        @Test
        void aPearlLeavesStraightWhereThePlayerLooks() {
            Velocity pearl = LegacyMath.throwVelocity(0f, 90f, SETTINGS.pearls().throwing(), noSpread);
            assertEquals(-1.5, pearl.y(), 1e-6, "looking straight down");
        }

        @Test
        void theSpreadFollowsTheInaccuracy() {
            Velocity spread = LegacyMath.throwVelocity(0f, 0f, SETTINGS.pearls().throwing(), new double[] {1, 0, 0});
            assertEquals(0.0075 * 1.5, spread.x(), 1e-9);
        }
    }

    @Nested
    class Settings {

        @Test
        void theDefaultsAreTheOldGamesValues() {
            assertTrue(SETTINGS.attackCooldown().remove());
            assertEquals(0.4, SETTINGS.knockback().horizontal());
            assertEquals(4.0, SETTINGS.regeneration().intervalSeconds());
            assertEquals(18, SETTINGS.regeneration().minimumFood());
            assertEquals(1.3, SETTINGS.strength().perLevel());
            assertEquals(5, SETTINGS.goldenApples().enchanted().effects().get(0).level(),
                    "the Notch apple gave Regeneration V");
        }

        @Test
        void weaponsDealTheOldGamesDamage() {
            var weapons = SETTINGS.weaponDamage();
            assertEquals(8.0, weapons.of("diamond_sword").orElseThrow(), "4 + diamond's 3 + the player's 1");
            assertEquals(5.0, weapons.of("wooden_sword").orElseThrow());
            assertEquals(7.0, weapons.of("diamond_axe").orElseThrow(), "an axe hit softer than a sword");
            assertEquals(6.0, weapons.of("iron_axe").orElseThrow());
            assertEquals(6.0, weapons.of("diamond_pickaxe").orElseThrow());
            assertEquals(5.0, weapons.of("diamond_shovel").orElseThrow());
            assertEquals(4.0, weapons.of("golden_axe").orElseThrow(), "gold was wood's damage");
            assertEquals(9.0, weapons.of("netherite_sword").orElseThrow());
        }

        @Test
        void anItemNotListedKeepsItsModernDamage() {
            var weapons = SETTINGS.weaponDamage();
            assertEquals(8.0, weapons.of("DIAMOND_SWORD").orElseThrow(), "item names are read without case");
            assertTrue(weapons.of("trident").isEmpty());
            assertTrue(weapons.of("wooden_hoe").isEmpty());
        }

        @Test
        void theModeIsReadWithoutCase() {
            assertEquals(CombatMode.CLASSIC, CombatMode.parse(" Classic ").orElseThrow());
            assertEquals(CombatMode.MODERN, CombatMode.parse("modern").orElseThrow());
            assertTrue(CombatMode.parse("1.7").isEmpty());
        }
    }
}
