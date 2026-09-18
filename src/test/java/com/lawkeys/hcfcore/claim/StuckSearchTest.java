package com.lawkeys.hcfcore.claim;

import com.lawkeys.hcfcore.util.ChunkPosition;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The /stuck search: a player walled into the middle of a claim is hard to
 * reproduce on a server and trivial to write down here.
 */
class StuckSearchTest {

    private static final String WORLD = "world";

    private static ChunkPosition at(int x, int z) {
        return new ChunkPosition(WORLD, x, z);
    }

    /** Already somewhere legal: the answer is to stay put, not to move them. */
    @Test
    void aPlayerWhoIsNotStuckStaysWhereTheyAre() {
        Optional<ChunkPosition> found = StuckSearch.nearest(at(0, 0), 10, chunk -> true);
        assertEquals(at(0, 0), found.orElseThrow());
    }

    @Test
    void theNearestFreeChunkWins() {
        Set<ChunkPosition> claimed = Set.of(at(0, 0), at(1, 0), at(-1, 0), at(0, 1), at(0, -1));
        ChunkPosition found = StuckSearch.nearest(at(0, 0), 10, c -> !claimed.contains(c)).orElseThrow();

        // Ring 1 has free corners, so the answer must be one chunk away, not further.
        assertEquals(1, Math.max(Math.abs(found.x()), Math.abs(found.z())));
    }

    /** Being thrown to the far side of the map is worse than the wall. */
    @Test
    void aBigClaimIsEscapedByItsNearestEdge() {
        Optional<ChunkPosition> found = StuckSearch.nearest(at(0, 0), 20,
                c -> Math.abs(c.x()) > 3 || Math.abs(c.z()) > 3);
        ChunkPosition landing = found.orElseThrow();
        assertEquals(4, Math.max(Math.abs(landing.x()), Math.abs(landing.z())),
                "the first ring outside the claim");
    }

    @Test
    void nothingIsFoundBeyondTheRadius() {
        assertTrue(StuckSearch.nearest(at(0, 0), 3, c -> Math.abs(c.x()) > 50).isEmpty());
    }

    @Test
    void aNegativeRadiusFindsNothingRatherThanThrowing() {
        assertTrue(StuckSearch.nearest(at(0, 0), -1, c -> true).isEmpty());
    }

    @Test
    void theSearchStaysInTheSameWorld() {
        ChunkPosition found = StuckSearch.nearest(new ChunkPosition("nether", 5, 5), 5,
                c -> !c.equals(new ChunkPosition("nether", 5, 5))).orElseThrow();
        assertEquals("nether", found.world());
    }

    /** Negative coordinates are where off-by-one ring maths usually shows up. */
    @Test
    void negativeCoordinatesAreSearchedToo() {
        Set<ChunkPosition> claimed = Set.of(at(-10, -10));
        ChunkPosition found = StuckSearch.nearest(at(-10, -10), 5,
                c -> !claimed.contains(c)).orElseThrow();
        assertEquals(1, Math.max(Math.abs(found.x() + 10), Math.abs(found.z() + 10)));
    }
}
