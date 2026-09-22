package com.lawkeys.hcfcore.claim.wand;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The wand's two corners. */
class SelectionTest {

    @Test
    void bothCornersMakeARectangleWhateverTheirOrder() {
        Selection selection = Selection.empty()
                .with(1, new Selection.Corner("world", 20, 70, -5))
                .with(2, new Selection.Corner("world", 10, 64, 4));
        assertTrue(selection.isComplete());
        assertEquals(10, selection.minX());
        assertEquals(-5, selection.minZ());
        assertEquals(11, selection.width());
        assertEquals(10, selection.length());
        assertEquals(110, selection.area());
        assertEquals(64, selection.minY());
        assertEquals(70, selection.maxY());
    }

    @Test
    void pickingACornerAgainReplacesIt() {
        Selection selection = Selection.empty()
                .with(1, new Selection.Corner("world", 0, 64, 0))
                .with(1, new Selection.Corner("world", 5, 64, 5));
        assertFalse(selection.isComplete());
        assertEquals(5, selection.first().x());
    }

    @Test
    void aCornerInAnotherWorldStartsOver() {
        Selection selection = Selection.empty()
                .with(1, new Selection.Corner("world", 0, 64, 0))
                .with(2, new Selection.Corner("world_nether", 5, 64, 5));
        assertFalse(selection.isComplete());
        assertNull(selection.first());
        assertEquals("world_nether", selection.world());
    }
}
