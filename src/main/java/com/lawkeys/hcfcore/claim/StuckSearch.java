package com.lawkeys.hcfcore.claim;

import com.lawkeys.hcfcore.util.ChunkPosition;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Finds the nearest chunk a stuck player may be put down in.
 *
 * <p>Pure Java: the caller says which chunks are unavailable, this decides which
 * one to aim for. That split is what makes the search testable - a player walled
 * into the middle of a claim is a hard situation to reproduce on a server and a
 * trivial one to write down here.
 *
 * <p>Searched in rings outward from the player, so the answer is always the
 * closest one: a /stuck that threw somebody to the far side of the map would be a
 * worse outcome than the wall they were stuck behind.
 */
public final class StuckSearch {

    private StuckSearch() {
    }

    /**
     * @param from         the chunk the player is in
     * @param maxRadius    how far out to look, in chunks
     * @param isAvailable  whether a chunk may be teleported into
     * @return the nearest available chunk, or empty if none is within the radius
     */
    public static Optional<ChunkPosition> nearest(ChunkPosition from, int maxRadius,
                                                  Predicate<ChunkPosition> isAvailable) {
        if (maxRadius < 0) {
            return Optional.empty();
        }
        // Ring 0 is the player's own chunk: if they are somehow already somewhere
        // legal, the answer is to stay put rather than to move them at all.
        for (int radius = 0; radius <= maxRadius; radius++) {
            Optional<ChunkPosition> found = searchRing(from, radius, isAvailable);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    /**
     * Walks the square ring at exactly this radius.
     *
     * <p>Only the edge, not the filled square: the inside was covered by smaller
     * radii, and re-testing it would make the search quadratic in the radius for no
     * new answers.
     */
    private static Optional<ChunkPosition> searchRing(ChunkPosition centre, int radius,
                                                      Predicate<ChunkPosition> isAvailable) {
        if (radius == 0) {
            return isAvailable.test(centre) ? Optional.of(centre) : Optional.empty();
        }
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                    continue;
                }
                ChunkPosition candidate =
                        new ChunkPosition(centre.world(), centre.x() + dx, centre.z() + dz);
                if (isAvailable.test(candidate)) {
                    return Optional.of(candidate);
                }
            }
        }
        return Optional.empty();
    }
}
