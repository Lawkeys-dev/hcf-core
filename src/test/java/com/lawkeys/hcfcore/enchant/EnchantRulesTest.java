package com.lawkeys.hcfcore.enchant;

import org.junit.jupiter.api.Test;

import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Custom enchants, without a server. */
class EnchantRulesTest {

    @Test
    void anItemIsReadFromItsMaterial() {
        assertEquals(EnchantTarget.HELMET, EnchantTarget.of("DIAMOND_HELMET").orElseThrow());
        assertEquals(EnchantTarget.HELMET, EnchantTarget.of("TURTLE_HELMET").orElseThrow());
        assertEquals(EnchantTarget.BOOTS, EnchantTarget.of("netherite_boots").orElseThrow());
        assertEquals(EnchantTarget.PICKAXE, EnchantTarget.of("IRON_PICKAXE").orElseThrow(),
                "a pickaxe is not an axe, whatever its name ends with");
        assertEquals(EnchantTarget.AXE, EnchantTarget.of("GOLDEN_AXE").orElseThrow());
        assertEquals(EnchantTarget.BOW, EnchantTarget.of("BOW").orElseThrow());
        assertTrue(EnchantTarget.of("STICK").isEmpty());
    }

    @Test
    void theConfigNamesKindsAndGroups() {
        assertEquals(EnchantTarget.ARMOR, EnchantTarget.parse("armor"));
        assertEquals(Set.of(EnchantTarget.SWORD, EnchantTarget.AXE), EnchantTarget.parse("weapon"));
        assertEquals(Set.of(EnchantTarget.BOOTS), EnchantTarget.parse(" Boots "));
        assertTrue(EnchantTarget.parse("wand").isEmpty());
    }

    /** The anvil's rule: a higher book replaces, an equal one adds one, up to the maximum. */
    @Test
    void aBookMergesLikeAnAnvil() {
        assertEquals(OptionalInt.of(2), EnchantLevels.merge(0, 2, 3));
        assertEquals(OptionalInt.of(3), EnchantLevels.merge(2, 2, 3));
        assertEquals(OptionalInt.of(3), EnchantLevels.merge(1, 3, 3));
        assertTrue(EnchantLevels.merge(3, 3, 3).isEmpty(), "at the maximum, nothing to add");
        assertTrue(EnchantLevels.merge(3, 2, 3).isEmpty(), "a lower book adds nothing");
        assertEquals(OptionalInt.of(2), EnchantLevels.merge(0, 5, 2), "a book above the maximum is brought down");
    }

    @Test
    void levelsAreWrittenInRomanNumerals() {
        assertEquals("I", EnchantLevels.roman(1));
        assertEquals("IV", EnchantLevels.roman(4));
        assertEquals("IX", EnchantLevels.roman(9));
        assertEquals("XIV", EnchantLevels.roman(14));
        assertEquals("40", EnchantLevels.roman(40));
    }
}
