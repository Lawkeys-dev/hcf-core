package com.lawkeys.hcfcore.events.king;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarzoneBorderTest {

    private static Set<String> keys(List<int[]> columns) {
        Set<String> keys = new HashSet<>();
        for (int[] column : columns) {
            keys.add(column[0] + ":" + column[1]);
        }
        return keys;
    }

    @Test
    void farFromTheEdgeThereIsNothingToDraw() {
        assertTrue(WarzoneBorder.columnsNear(0, 0, 120, 0, 0, 15).isEmpty());
    }

    @Test
    void nearAnEdgeOnlyItsColumnsWithinTheRadiusAreDrawn() {
        List<int[]> columns = WarzoneBorder.columnsNear(0, 0, 120, 115, 0, 15);
        for (int[] column : columns) {
            assertEquals(120, column[0], "the east edge only");
            assertTrue(Math.abs(column[1]) <= 15);
        }
        assertTrue(keys(columns).contains("120:0"));
        assertTrue(!keys(columns).contains("120:15"), "a circle, not a square: 5 across and 15 along is too far");
    }

    @Test
    void aCornerDrawsBothEdgesAndEachColumnOnce() {
        List<int[]> columns = WarzoneBorder.columnsNear(0, 0, 120, 118, 118, 5);
        assertTrue(keys(columns).contains("120:120"));
        assertTrue(keys(columns).contains("120:118"));
        assertTrue(keys(columns).contains("118:120"));
        assertEquals(keys(columns).size(), columns.size(), "no column twice");
    }
}
