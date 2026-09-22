package com.lawkeys.hcfcore.claim;

import com.lawkeys.hcfcore.util.ChunkPosition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Turns the chunk claims of versions up to 0.7 into block claims, once, when a
 * server first starts on this version - the project owner's choice, 22/09/2026:
 * nothing lost, nothing paid.
 *
 * <p>A team's chunks are covered by rectangles, greedily: from the northernmost,
 * westernmost chunk not yet covered, as far east as the run of chunks goes, then as
 * far south as whole rows of that run go. Every chunk lands in exactly one rectangle
 * and no rectangle covers a chunk the team did not own, so the land is the same land,
 * block for block. Pure, and tested.
 */
public final class LegacyChunkClaims {

    private LegacyChunkClaims() {
    }

    /**
     * @param chunks    one team's chunks, in any world
     * @param claimedAt when the team first claimed, kept on every rectangle
     * @return the rectangles covering exactly those chunks, paid nothing
     */
    public static List<ClaimArea> toAreas(UUID teamId, Collection<ChunkPosition> chunks, long claimedAt) {
        Set<ChunkPosition> left = new HashSet<>(chunks);
        List<ChunkPosition> order = new ArrayList<>(chunks);
        order.sort(Comparator.comparing(ChunkPosition::world)
                .thenComparingInt(ChunkPosition::z)
                .thenComparingInt(ChunkPosition::x));
        List<ClaimArea> areas = new ArrayList<>();
        for (ChunkPosition start : order) {
            if (!left.contains(start)) {
                continue;
            }
            int endX = start.x();
            while (left.contains(new ChunkPosition(start.world(), endX + 1, start.z()))) {
                endX++;
            }
            int endZ = start.z();
            while (rowFree(left, start.world(), start.x(), endX, endZ + 1)) {
                endZ++;
            }
            for (int z = start.z(); z <= endZ; z++) {
                for (int x = start.x(); x <= endX; x++) {
                    left.remove(new ChunkPosition(start.world(), x, z));
                }
            }
            areas.add(new ClaimArea(UUID.randomUUID(), teamId, start.world(),
                    start.x() << 4, start.z() << 4, (endX << 4) + 15, (endZ << 4) + 15, 0.0, claimedAt));
        }
        return areas;
    }

    private static boolean rowFree(Set<ChunkPosition> left, String world, int fromX, int toX, int z) {
        for (int x = fromX; x <= toX; x++) {
            if (!left.contains(new ChunkPosition(world, x, z))) {
                return false;
            }
        }
        return true;
    }
}
