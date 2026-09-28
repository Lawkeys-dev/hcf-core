package com.lawkeys.hcfcore.claim;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UncoveredTest {

    private static final UUID TEAM = UUID.randomUUID();

    private static ClaimArea claim(String world, int x1, int z1, int x2, int z2) {
        return ClaimArea.between(TEAM, world, x1, z1, x2, z2, 0.0, 0L);
    }

    private static long area(List<int[]> parts) {
        return parts.stream().mapToLong(p -> (long) (p[2] - p[0] + 1) * (p[3] - p[1] + 1)).sum();
    }

    @Test
    void landInsideAClaimLeavesNothing() {
        assertTrue(Uncovered.of("world", 5, 5, 10, 10, List.of(claim("world", 0, 0, 20, 20))).isEmpty());
    }

    @Test
    void landWithNoClaimIsAllLeft() {
        List<int[]> left = Uncovered.of("world", 5, 5, 10, 10, List.of(claim("nether", 0, 0, 20, 20)));
        assertEquals(1, left.size());
        assertEquals(36, area(left));
    }

    @Test
    void aZoneAcrossTheEdgeLeavesTheStripOutside() {
        List<int[]> left = Uncovered.of("world", 15, 0, 25, 10, List.of(claim("world", 0, 0, 20, 20)));
        assertEquals(1, left.size());
        assertEquals(List.of(21, 0, 25, 10), List.of(left.get(0)[0], left.get(0)[1], left.get(0)[2], left.get(0)[3]));
    }

    @Test
    void aClaimInTheMiddleLeavesARingThatNeverOverlapsItself() {
        List<int[]> left = Uncovered.of("world", 0, 0, 10, 10, List.of(claim("world", 3, 3, 6, 6)));
        assertEquals(121 - 16, area(left));
        for (int i = 0; i < left.size(); i++) {
            for (int j = i + 1; j < left.size(); j++) {
                int[] a = left.get(i);
                int[] b = left.get(j);
                boolean overlap = a[0] <= b[2] && b[0] <= a[2] && a[1] <= b[3] && b[1] <= a[3];
                assertTrue(!overlap, "parts overlap");
            }
        }
    }

    @Test
    void severalClaimsCoverTogether() {
        assertTrue(Uncovered.of("world", 0, 0, 9, 9,
                List.of(claim("world", 0, 0, 4, 9), claim("world", 5, 0, 9, 9))).isEmpty());
    }
}
