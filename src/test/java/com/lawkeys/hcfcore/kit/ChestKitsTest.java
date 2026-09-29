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
    void whatDoesNotFitIsLeftOut() {
        List<String> many = new ArrayList<>(Collections.nCopies(40, "STONE"));
        int[] slots = ChestKits.slots(many);
        assertEquals(35, slots[35]);
        assertEquals(-1, slots[36]);
    }
}
