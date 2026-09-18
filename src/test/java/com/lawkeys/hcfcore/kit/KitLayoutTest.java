package com.lawkeys.hcfcore.kit;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The kit layout editor's rules: where a kit's items go, for one player. */
class KitLayoutTest {

    private static boolean[] filled(int... slots) {
        boolean[] filled = new boolean[KitLayout.SLOTS];
        for (int slot : slots) {
            filled[slot] = true;
        }
        return filled;
    }

    @Test
    void withNoLayoutEveryItemStaysWhereTheKitHasIt() {
        int[] to = KitLayout.NONE.placement(filled(0, 1, 20, 36, 40));
        assertEquals(0, to[0]);
        assertEquals(1, to[1]);
        assertEquals(20, to[20]);
        assertEquals(36, to[36]);
        assertEquals(40, to[40]);
        assertEquals(-1, to[2], "an empty slot goes nowhere");
    }

    @Test
    void aLayoutMovesItems() {
        // The sword to the third hotbar slot, the pearls to the off hand.
        KitLayout layout = KitLayout.of(Map.of(0, 2, 1, 40));
        int[] to = layout.placement(filled(0, 1));
        assertEquals(2, to[0]);
        assertEquals(40, to[1]);
    }

    /** Two items swapped is the common case: each move wins over the other staying put. */
    @Test
    void aSwapIsTwoMoves() {
        KitLayout layout = KitLayout.of(Map.of(0, 1, 1, 0));
        int[] to = layout.placement(filled(0, 1));
        assertEquals(1, to[0]);
        assertEquals(0, to[1]);
    }

    @Test
    void armourIsNeverMoved() {
        KitLayout layout = KitLayout.of(Map.of(36, 5, 5, 37));
        assertTrue(layout.isEmpty(), "neither move touches only movable slots");
        int[] to = layout.placement(filled(36, 5));
        assertEquals(36, to[36]);
        assertEquals(5, to[5]);
    }

    /**
     * A layout saved for a kit that has since been re-created: the move lands on a slot
     * the new kit fills itself, and nothing is lost.
     */
    @Test
    void noItemIsEverLostWhateverTheLayoutSays() {
        KitLayout layout = KitLayout.of(Map.of(3, 0));
        int[] to = layout.placement(filled(0, 3));
        assertEquals(0, to[3], "the move wins");
        assertEquals(1, to[0], "and the item it displaced takes the first free slot");

        // Every movable slot filled, every item moved one along: still a permutation.
        Map<Integer, Integer> rotate = new java.util.HashMap<>();
        boolean[] all = new boolean[KitLayout.SLOTS];
        for (int slot = 0; slot < KitLayout.SLOTS; slot++) {
            all[slot] = true;
            if (slot < 35) {
                rotate.put(slot, slot + 1);
            }
        }
        rotate.put(35, 40);
        rotate.put(40, 0);
        int[] rotated = KitLayout.of(rotate).placement(all);
        Set<Integer> destinations = new HashSet<>();
        for (int destination : rotated) {
            assertTrue(destinations.add(destination), "slot " + destination + " given twice");
        }
        assertEquals(KitLayout.SLOTS, destinations.size());
    }

    @Test
    void theEditorShowsTheInventoryAsThePlayerSeesIt() {
        assertEquals(9, KitLayout.toPlayerSlot(0), "the storage rows on top");
        assertEquals(35, KitLayout.toPlayerSlot(26));
        assertEquals(0, KitLayout.toPlayerSlot(27), "the hotbar under them");
        assertEquals(8, KitLayout.toPlayerSlot(35));
        assertEquals(KitLayout.OFF_HAND, KitLayout.toPlayerSlot(KitLayout.EDITOR_OFF_HAND));
        assertEquals(-1, KitLayout.toPlayerSlot(44), "the bottom row is buttons");
        for (int editor = 0; editor < KitLayout.EDITOR_SIZE; editor++) {
            int player = KitLayout.toPlayerSlot(editor);
            if (player >= 0) {
                assertEquals(editor, KitLayout.toEditorSlot(player), "round trip of editor slot " + editor);
            }
        }
        assertEquals(-1, KitLayout.toEditorSlot(37), "armour is not in the editor");
    }

    @Test
    void itIsStoredAsText() {
        KitLayout layout = KitLayout.of(Map.of(0, 2, 1, 40, 7, 7));
        assertEquals("0:2,1:40", layout.format(), "a move that stays put is not a move");
        assertEquals(layout, KitLayout.parse(layout.format()));
        assertSame(KitLayout.NONE, KitLayout.parse(""));
        assertEquals(KitLayout.of(Map.of(0, 2)), KitLayout.parse("0:2, junk, 3:x, 99:1, 5:2"),
                "what cannot be read is skipped, and a second move to a taken slot too");
    }
}
