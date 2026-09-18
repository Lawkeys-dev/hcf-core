package com.lawkeys.hcfcore.limiter;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Enchantment and potion caps, without a server. */
class LevelCapsTest {

    private static final String SHARPNESS = "minecraft:sharpness";
    private static final String PROTECTION = "minecraft:protection";

    private static LevelCaps caps(Map<String, Integer> raw) {
        return LevelCaps.of(raw, message -> { });
    }

    @Nested
    class Names {

        @Test
        void aBareNameGetsTheMinecraftNamespace() {
            assertEquals(SHARPNESS, LevelCaps.normalize("Sharpness"));
            assertEquals(SHARPNESS, LevelCaps.normalize(" minecraft:SHARPNESS "));
            assertEquals(SHARPNESS, LevelCaps.normalize(":sharpness"));
        }

        /** A datapack's enchantment keeps its own namespace. */
        @Test
        void anotherNamespaceIsKept() {
            assertEquals("mypack:lifesteal", LevelCaps.normalize("mypack:lifesteal"));
        }

        @Test
        void aBlankNameIsNoName() {
            assertNull(LevelCaps.normalize("  "));
            assertNull(LevelCaps.normalize(null));
        }

        /** Written either way in the file, it is the same cap. */
        @Test
        void capsAreFoundWhateverTheSpellingInTheFile() {
            assertEquals(2, caps(Map.of("SHARPNESS", 2)).capOf(SHARPNESS).orElseThrow());
        }

        @Test
        void aNegativeCapIsDroppedWithAWarning() {
            List<String> warnings = new ArrayList<>();
            LevelCaps loaded = LevelCaps.of(Map.of("sharpness", -1), warnings::add);
            assertTrue(loaded.isEmpty());
            assertEquals(1, warnings.size());
        }

        @Test
        void theSameEnchantmentTwiceKeepsTheLastAndSaysSo() {
            Map<String, Integer> raw = new LinkedHashMap<>();
            raw.put("sharpness", 1);
            raw.put("minecraft:sharpness", 3);
            List<String> warnings = new ArrayList<>();
            assertEquals(3, LevelCaps.of(raw, warnings::add).capOf(SHARPNESS).orElseThrow());
            assertEquals(1, warnings.size());
        }
    }

    @Nested
    class Clamping {

        @Test
        void onlyACapOfZeroForbids() {
            LevelCaps caps = caps(Map.of("strength", 0, "speed", 1));
            assertTrue(caps.forbids("minecraft:strength"), "Strength left to the Bard");
            assertFalse(caps.forbids("minecraft:speed"), "capped, brought down, not forbidden");
            assertFalse(caps.forbids("minecraft:regeneration"), "not listed");
        }

        private final LevelCaps caps = caps(Map.of("sharpness", 2, "fire_aspect", 0, "power", 7));

        @Test
        void somethingUnlistedKeepsItsLevel() {
            assertEquals(4, caps.clamp(PROTECTION, 4));
        }

        @Test
        void aLevelAboveItsCapComesDown() {
            assertEquals(2, caps.clamp(SHARPNESS, 5));
            assertEquals(2, caps.clamp(SHARPNESS, 2));
            assertEquals(1, caps.clamp(SHARPNESS, 1));
        }

        @Test
        void aCapOfZeroForbidsIt() {
            assertEquals(0, caps.clamp("minecraft:fire_aspect", 2));
        }

        /** A cap above vanilla's maximum raises nothing on its own: only the anvil climbs to it. */
        @Test
        void clampingNeverRaisesALevel() {
            assertEquals(5, caps.clamp("minecraft:power", 5));
        }
    }

    @Nested
    class Anvil {

        @Test
        void somethingUnlistedIsLeftToVanilla() {
            assertEquals(5, caps(Map.of()).combine(SHARPNESS, 5, 5, 5));
        }

        /** The point of a cap above vanilla: two Sharpness V make VI, where vanilla stops at V. */
        @Test
        void twoEqualLevelsClimbPastVanillaToTheCap() {
            LevelCaps six = caps(Map.of("sharpness", 6));
            assertEquals(6, six.combine(SHARPNESS, 5, 5, 5));
            assertEquals(6, six.combine(SHARPNESS, 6, 6, 5), "but never past the cap");
        }

        @Test
        void unequalLevelsKeepTheHigher() {
            assertEquals(5, caps(Map.of("sharpness", 6)).combine(SHARPNESS, 3, 5, 5));
        }

        @Test
        void aCapBelowVanillaBringsTheResultDown() {
            LevelCaps two = caps(Map.of("sharpness", 2));
            assertEquals(2, two.combine(SHARPNESS, 5, 5, 5));
            assertEquals(2, two.combine(SHARPNESS, 1, 1, 2), "I and I make II, which is allowed");
            assertEquals(2, two.combine(SHARPNESS, 2, 2, 3), "II and II would make III");
        }

        /** A level carried only by the item going in is still capped on the way out. */
        @Test
        void aLevelFromOneSideOnlyIsCapped() {
            assertEquals(2, caps(Map.of("sharpness", 2)).combine(SHARPNESS, 4, 0, 4));
        }

        @Test
        void aForbiddenEnchantmentLeavesTheResult() {
            assertEquals(0, caps(Map.of("sharpness", 0)).combine(SHARPNESS, 1, 1, 2));
        }
    }
}
