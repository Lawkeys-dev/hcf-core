package com.lawkeys.hcfcore.kit;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ChestKitsTest {

    @Test
    void armourIsWornAndTheRestFillsTheInventoryInOrder() {
        int[] slots = ChestKits.slots(Arrays.asList("DIAMOND_HELMET", "DIAMOND_CHESTPLATE", "DIAMOND_LEGGINGS",
                "DIAMOND_BOOTS", "DIAMOND_SWORD", null, "ENDER_PEARL"));
        assertArrayEquals(new int[]{39, 38, 37, 36, 0, -1, 1}, slots);
    }

    @Test
    void aSecondSetOfArmourIsCarried() {
        int[] slots = ChestKits.slots(List.of("IRON_BOOTS", "GOLDEN_BOOTS"));
        assertArrayEquals(new int[]{36, 0}, slots);
    }

    @Test
    void aDoubleChestIsTakenAsItIs() {
        List<String> chest = new ArrayList<>(Collections.nCopies(54, "SPLASH_POTION"));
        chest.set(0, "DIAMOND_HELMET");
        chest.set(1, null);
        int[] slots = ChestKits.slots(chest);
        assertEquals(0, slots[0], "no armour worn");
        assertEquals(-1, slots[1]);
        assertEquals(53, slots[53]);
        assertEquals(54, ChestKits.size(54));
        assertEquals(41, ChestKits.size(27));
    }

    @Test
    void whatDoesNotFitIsLeftOut() {
        List<String> many = new ArrayList<>(Collections.nCopies(40, "STONE"));
        int[] slots = ChestKits.slots(many);
        assertEquals(35, slots[35]);
        assertEquals(-1, slots[36]);
    }
}
