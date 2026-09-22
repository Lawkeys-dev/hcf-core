package com.lawkeys.hcfcore.claim;

import com.lawkeys.hcfcore.util.ChunkPosition;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A claim's geometry, and turning the chunk claims of 0.7 into block claims. */
class ClaimAreaTest {

    private static final UUID TEAM = UUID.randomUUID();

    private static ClaimArea area(int x1, int z1, int x2, int z2) {
        return ClaimArea.between(TEAM, "world", x1, z1, x2, z2, 0.0, 0L);
    }

    @Test
    void bothCornersAreInside() {
        ClaimArea claim = area(10, 20, 14, 30);
        assertEquals(5, claim.width());
        assertEquals(11, claim.length());
        assertEquals(55, claim.area());
        assertTrue(claim.contains("world", 10, 20));
        assertTrue(claim.contains("world", 14, 30));
        assertFalse(claim.contains("world", 15, 30));
        assertFalse(claim.contains("world_nether", 12, 25));
    }

    @Test
    void cornersInAnyOrderMakeTheSameRectangle() {
        ClaimArea claim = area(14, 30, 10, 20);
        assertEquals(10, claim.minX());
        assertEquals(20, claim.minZ());
        assertEquals(14, claim.maxX());
        assertEquals(30, claim.maxZ());
        assertThrows(IllegalArgumentException.class,
                () -> new ClaimArea(UUID.randomUUID(), TEAM, "world", 5, 0, 4, 0, 0.0, 0L));
    }

    @Test
    void theGapIsTheLandBetween() {
        ClaimArea claim = area(0, 0, 9, 9);
        assertEquals(0, claim.gapTo(area(10, 0, 20, 9)), "edge to edge");
        assertEquals(0, claim.gapTo(area(5, 5, 20, 20)), "overlapping");
        assertEquals(7, claim.gapTo(area(17, 0, 20, 9)), "blocks 10 to 16 between");
        assertEquals(5, claim.gapTo(area(15, 12, 20, 20)), "the longer way round counts");
        assertEquals(Integer.MAX_VALUE, claim.gapTo(ClaimArea.between(TEAM, "world_nether", 0, 0, 9, 9, 0, 0)));
    }

    @Test
    void touchingIsSharingAnEdgeNotACorner() {
        ClaimArea claim = area(0, 0, 9, 9);
        assertTrue(claim.touches(area(10, 3, 20, 5)));
        assertTrue(claim.touches(area(-5, -5, 20, -1)), "a longer neighbour along the north side");
        assertFalse(claim.touches(area(10, 10, 20, 20)), "a corner only");
        assertFalse(claim.touches(area(11, 0, 20, 9)), "a block apart");
        assertFalse(claim.touches(area(5, 5, 20, 20)), "overlapping is not touching");
    }

    @Test
    void theChunksCoveredIncludeTheOnesTouchedInPart() {
        assertEquals(List.of(new ChunkPosition("world", -1, 0), new ChunkPosition("world", 0, 0)),
                area(-1, 0, 5, 5).chunks());
        assertEquals(4, area(15, 15, 16, 16).chunks().size());
    }

    @Test
    void chunkClaimsBecomeRectanglesCoveringExactlyTheSameLand() {
        Set<ChunkPosition> chunks = Set.of(
                new ChunkPosition("world", 0, 0), new ChunkPosition("world", 1, 0), new ChunkPosition("world", 2, 0),
                new ChunkPosition("world", 0, 1), new ChunkPosition("world", 1, 1),
                new ChunkPosition("world", 5, 5), new ChunkPosition("world_nether", -3, -3));
        List<ClaimArea> areas = LegacyChunkClaims.toAreas(TEAM, chunks, 9L);

        Set<ChunkPosition> covered = new HashSet<>();
        for (ClaimArea claim : areas) {
            assertEquals(0.0, claim.pricePaid());
            assertEquals(9L, claim.claimedAt());
            assertEquals(0, Math.floorMod(claim.minX(), 16), "chunk-aligned");
            assertEquals(15, Math.floorMod(claim.maxZ(), 16));
            for (ChunkPosition chunk : claim.chunks()) {
                assertTrue(covered.add(chunk), "no chunk in two rectangles: " + chunk);
            }
        }
        assertEquals(chunks, covered, "every chunk, and only those");
        assertTrue(areas.size() <= 4, "merged into few rectangles, not one per chunk: " + areas.size());
    }
}
