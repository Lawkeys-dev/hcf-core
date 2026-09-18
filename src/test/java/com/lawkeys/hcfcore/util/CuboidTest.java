package com.lawkeys.hcfcore.util;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The geometry two unrelated modules now share: a capture event's zone and a
 * resource node's region.
 *
 * <p>The containment rules were already covered through the events module; what
 * matters here is the part that is new, and the part where a sign error is easy
 * and invisible - chunk overlap and negative coordinates.
 */
class CuboidTest {

    @Nested
    class Bounds {

        @Test
        void cornersMayBeGivenInEitherOrder() {
            Cuboid walked = Cuboid.between("world", 15, 80, 15, 0, 60, 0);
            assertEquals(Cuboid.between("world", 0, 60, 0, 15, 80, 15), walked);
        }

        @Test
        void boundsAreInclusive() {
            Cuboid box = Cuboid.between("world", 0, 60, 0, 15, 80, 15);
            assertTrue(box.containsBlock("world", 15, 80, 15));
            assertFalse(box.containsBlock("world", 16, 80, 15));
        }

        @Test
        void aBoxIsBoundToItsWorld() {
            Cuboid box = Cuboid.between("world", 0, 60, 0, 15, 80, 15);
            assertTrue(box.containsBlock("WORLD", 1, 61, 1), "world names are case-insensitive");
            assertFalse(box.containsBlock("world_nether", 1, 61, 1));
        }

        @Test
        void theBlockCountDoesNotOverflowOnALargeRegion() {
            // 2000 blocks a side is silly but configurable, and it overflows an int.
            Cuboid huge = Cuboid.between("world", 0, 0, 0, 1999, 1999, 1999);
            assertEquals(2000L * 2000L * 2000L, huge.blockCount());
        }

        @Test
        void aSingleBlockRegionHoldsOneBlock() {
            assertEquals(1L, Cuboid.between("world", 5, 5, 5, 5, 5, 5).blockCount());
        }
    }

    @Nested
    class HeightClamping {

        private final Cuboid tall = Cuboid.between("world", 0, -100, 0, 15, 400, 15);

        @Test
        void aRegionTallerThanItsWorldIsCutDownToIt() {
            Cuboid clamped = tall.withYClampedTo(-64, 319).orElseThrow();
            assertEquals(-64, clamped.minY());
            assertEquals(319, clamped.maxY());
            assertEquals(0, clamped.minX(), "only the vertical range is touched");
            assertEquals(15, clamped.maxX());
        }

        @Test
        void aRegionThatAlreadyFitsIsReturnedUntouched() {
            Cuboid fits = Cuboid.between("world", 0, 60, 0, 15, 80, 15);
            assertSame(fits, fits.withYClampedTo(-64, 319).orElseThrow());
        }

        @Test
        void aRegionEntirelyOutsideItsWorldClampsToNothing() {
            // A typo, not a mountain. Better to say so than to walk 300 000 positions
            // that cannot hold a block.
            assertTrue(Cuboid.between("world", 0, 500, 0, 15, 600, 15)
                    .withYClampedTo(-64, 319).isEmpty());
        }
    }

    @Nested
    class ChunkOverlap {

        @Test
        void aRegionInsideOneChunkOverlapsOnlyThatChunk() {
            Cuboid box = Cuboid.between("world", 0, 60, 0, 15, 80, 15);
            assertTrue(box.overlaps(new ChunkPosition("world", 0, 0)));
            assertFalse(box.overlaps(new ChunkPosition("world", 1, 0)));
            assertFalse(box.overlaps(new ChunkPosition("world", 0, 1)));
        }

        @Test
        void aRegionClippingACornerStillOverlapsThatChunk() {
            // One block into the next chunk is still land the region sits on, and a
            // team claiming it would own part of the mountain.
            Cuboid box = Cuboid.between("world", 10, 60, 10, 16, 80, 16);
            assertTrue(box.overlaps(new ChunkPosition("world", 0, 0)));
            assertTrue(box.overlaps(new ChunkPosition("world", 1, 1)));
        }

        @Test
        void negativeCoordinatesLandInTheRightChunk() {
            // Block -1 is in chunk -1, not chunk 0: integer division would round
            // towards zero here and quietly leave half the region claimable.
            Cuboid box = Cuboid.between("world", -16, 60, -16, -1, 80, -1);
            assertTrue(box.overlaps(new ChunkPosition("world", -1, -1)));
            assertFalse(box.overlaps(new ChunkPosition("world", 0, 0)));
        }

        @Test
        void anotherWorldNeverOverlaps() {
            Cuboid box = Cuboid.between("world", 0, 60, 0, 15, 80, 15);
            assertFalse(box.overlaps(new ChunkPosition("world_nether", 0, 0)));
        }

        @Test
        void theChunkBoundsAreTheChunksTheBoxSpans() {
            // -17 is in chunk -2 and -16 in chunk -1: the bounds a refill loads must
            // include the partial chunks at both ends, negative side included.
            Cuboid box = Cuboid.between("world", -17, 60, -1, 16, 80, 0);
            assertEquals(-2, box.minChunkX());
            assertEquals(1, box.maxChunkX());
            assertEquals(-1, box.minChunkZ());
            assertEquals(0, box.maxChunkZ());
        }
    }
}
