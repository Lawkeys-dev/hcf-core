package com.lawkeys.hcfcore.claim.view;

import com.lawkeys.hcfcore.claim.ClaimArea;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BorderColumnsTest {

    private static ClaimArea area(int minX, int minZ, int maxX, int maxZ) {
        return new ClaimArea(UUID.randomUUID(), UUID.randomUUID(), "world", minX, minZ, maxX, maxZ, 0.0, 0L);
    }

    @Test
    void theFourCornersAreDrawnWhenTheyAreCloseEnough() {
        ClaimArea claim = area(0, 0, 10, 10);
        assertEquals(4, BorderColumns.corners(claim, 5, 5, 32).size());
        List<int[]> close = BorderColumns.corners(claim, 0, 0, 4);
        assertEquals(1, close.size());
        assertEquals(0, close.get(0)[0]);
        assertTrue(BorderColumns.corners(claim, 500, 500, 32).isEmpty());
    }

    @Test
    void theOutlineIsTheEdgeOfTheClaimOnlyOnce() {
        // A 5x5 claim: 25 blocks, 16 of them on the edge.
        assertEquals(16, BorderColumns.outline(area(0, 0, 4, 4), 2, 2, 32).size());
        // One block wide: every column is an edge.
        assertEquals(5, BorderColumns.outline(area(0, 0, 0, 4), 0, 2, 32).size());
    }

    @Test
    void theOutlineIsCutToWhatIsNearThePlayer() {
        ClaimArea claim = area(0, 0, 100, 100);
        List<int[]> near = BorderColumns.outline(claim, 0, 0, 5);
        // The corner at 0,0: six blocks along each edge, the corner counted once.
        assertEquals(11, near.size());
        assertTrue(BorderColumns.outline(claim, 50, 50, 5).isEmpty(), "the middle of a claim has no edge near it");
    }

    @Test
    void oneBlockInEveryFewIsTheMarker() {
        assertTrue(BorderColumns.isMarker(0, 6));
        assertFalse(BorderColumns.isMarker(1, 6));
        assertFalse(BorderColumns.isMarker(5, 6));
        assertTrue(BorderColumns.isMarker(6, 6));
        assertTrue(BorderColumns.isMarker(12, 6));
        assertTrue(BorderColumns.isMarker(3, 1), "every block is the marker when every is 1");
    }

    @Test
    void theDistanceToAClaimIsZeroInsideAndSquareOutside() {
        ClaimArea claim = area(0, 0, 10, 10);
        assertEquals(0, BorderColumns.distanceTo(claim, 5, 5));
        assertEquals(0, BorderColumns.distanceTo(claim, 0, 10));
        assertEquals(1, BorderColumns.distanceTo(claim, -1, 5));
        assertEquals(5, BorderColumns.distanceTo(claim, 15, 5));
        assertEquals(5, BorderColumns.distanceTo(claim, 15, 15), "a corner is as far as the sides");
    }
}
