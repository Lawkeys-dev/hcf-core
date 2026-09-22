package com.lawkeys.hcfcore.claim.view;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ColumnHeightsTest {

    @Test
    void aColumnRunsFromAboveTheGroundToTheGivenLayer() {
        assertArrayEquals(new int[]{-59, 128}, ColumnHeights.range(-60, 128, 3, 320));
        assertArrayEquals(new int[]{65, 128}, ColumnHeights.range(64, 128, 3, 320));
    }

    @Test
    void groundAboveThatLayerStillGetsTheMinimum() {
        assertArrayEquals(new int[]{201, 203}, ColumnHeights.range(200, 128, 3, 320));
    }

    @Test
    void theBuildLimitIsNeverDrawnThrough() {
        assertArrayEquals(new int[]{318, 319}, ColumnHeights.range(317, 128, 3, 320));
        assertNull(ColumnHeights.range(319, 128, 3, 320));
        assertNull(ColumnHeights.range(400, 128, 3, 320));
    }
}
