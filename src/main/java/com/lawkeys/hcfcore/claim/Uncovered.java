package com.lawkeys.hcfcore.claim;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * What of a rectangle of land some claims leave uncovered, as rectangles: the land
 * still to claim so that the whole of it is held - an event's capture zone drawn
 * past the edge of the event's territory, which the territory then grows to take in.
 *
 * <p>Pure Java, block-precise and inclusive like {@link ClaimArea}.
 */
public final class Uncovered {

    private Uncovered() {
    }

    /**
     * @param covering the claims already there, in any world - only {@code world}'s count
     * @return the uncovered parts, each {@code {minX, minZ, maxX, maxZ}}; none when the
     *         claims cover it all
     */
    public static List<int[]> of(String world, int minX, int minZ, int maxX, int maxZ,
                                 Collection<ClaimArea> covering) {
        List<int[]> left = new ArrayList<>();
        left.add(new int[]{minX, minZ, maxX, maxZ});
        for (ClaimArea claim : covering) {
            if (!claim.world().equals(world)) {
                continue;
            }
            List<int[]> next = new ArrayList<>();
            for (int[] part : left) {
                cut(part, claim, next);
            }
            left = next;
            if (left.isEmpty()) {
                break;
            }
        }
        return left;
    }

    /** Adds to {@code out} what of {@code part} lies outside {@code claim}: up to four strips. */
    private static void cut(int[] part, ClaimArea claim, List<int[]> out) {
        int x1 = Math.max(part[0], claim.minX());
        int z1 = Math.max(part[1], claim.minZ());
        int x2 = Math.min(part[2], claim.maxX());
        int z2 = Math.min(part[3], claim.maxZ());
        if (x1 > x2 || z1 > z2) {
            out.add(part);
            return;
        }
        // West and east strips, full length; north and south between them.
        if (part[0] < x1) {
            out.add(new int[]{part[0], part[1], x1 - 1, part[3]});
        }
        if (x2 < part[2]) {
            out.add(new int[]{x2 + 1, part[1], part[2], part[3]});
        }
        if (part[1] < z1) {
            out.add(new int[]{x1, part[1], x2, z1 - 1});
        }
        if (z2 < part[3]) {
            out.add(new int[]{x1, z2 + 1, x2, part[3]});
        }
    }
}
