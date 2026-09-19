package com.lawkeys.hcfcore.theme;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Menus framed by the theme: rows, items centred inside the frame, pages. */
class MenuLayoutTest {

    @Test
    void aFewItemsInAFullFrameAreSpreadOnOneRow() {
        MenuLayout layout = MenuLayout.of(4, "full", 0);
        assertEquals(3, layout.rows());
        assertEquals(List.of(10, 12, 14, 16), layout.itemSlots(), "centred, one cell apart");
        assertEquals(Set.of(0, 8, 18, 26), layout.cornerSlots());
        assertEquals(1, layout.pages());
    }

    @Test
    void moreItemsFillRowsAndCentreTheLast() {
        MenuLayout layout = MenuLayout.of(12, "full", 0);
        assertEquals(4, layout.rows());
        assertEquals(List.of(10, 11, 12, 13, 14, 15, 16, 20, 21, 22, 23, 24), layout.itemSlots());
    }

    @Test
    void noItemSitsOnTheFrame() {
        for (String frame : List.of("full", "bars", "none")) {
            for (int count = 0; count < 80; count++) {
                MenuLayout layout = MenuLayout.of(count, frame, 0);
                Set<Integer> seen = new HashSet<>();
                for (int slot : layout.itemSlots()) {
                    assertFalse(layout.frameSlots().contains(slot), frame + " " + count + ": " + slot);
                    assertTrue(slot >= 0 && slot < layout.size());
                    assertTrue(seen.add(slot), "one item per slot");
                    assertTrue(slot != layout.previous() && slot != layout.next());
                }
                assertTrue(layout.rows() >= 1 && layout.rows() <= 6);
            }
        }
    }

    @Test
    void tooManyForOneMenuArePaged() {
        MenuLayout first = MenuLayout.of(42, "full", 0);
        assertEquals(6, first.rows());
        assertEquals(2, first.pages());
        assertEquals(28, first.itemSlots().size());
        assertEquals(-1, first.previous(), "no page before the first");
        assertEquals(50, first.next());
        MenuLayout second = MenuLayout.of(42, "full", 1);
        assertEquals(28, second.firstItem());
        assertEquals(14, second.itemSlots().size());
        assertEquals(48, second.previous());
        assertEquals(-1, second.next());
        assertEquals(1, MenuLayout.of(42, "full", 9).page(), "a page out of range is the last");
    }

    @Test
    void withoutAFrameTheArrowsTakeTheLastRow() {
        MenuLayout layout = MenuLayout.of(60, "none", 0);
        assertEquals(6, layout.rows());
        assertEquals(45, layout.itemSlots().size());
        assertTrue(layout.frameSlots().isEmpty());
        assertEquals(50, layout.next());
    }
}
