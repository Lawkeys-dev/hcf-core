package com.lawkeys.hcfcore.claim.view;

import com.lawkeys.hcfcore.claim.ClaimArea;

import java.util.ArrayList;
import java.util.List;

/**
 * Which columns of a claim to draw: the four corners for {@code /team map}, the whole
 * border for a locked claim's wall - and, either way, only what is close enough to
 * the player to be worth sending.
 *
 * <p>Pure Java: no server API, so it is unit-tested without one.
 */
public final class BorderColumns {

    private BorderColumns() {
    }

    /**
     * @param radius how far from {@code (x, z)} to look, in blocks, as a square
     * @return the claim's four corners that fall inside the square, each as
     *         {@code {x, z}}; a claim whose corners are all further away draws none
     */
    public static List<int[]> corners(ClaimArea area, int x, int z, int radius) {
        List<int[]> columns = new ArrayList<>();
        int[][] corners = {{area.minX(), area.minZ()}, {area.maxX(), area.minZ()},
                {area.minX(), area.maxZ()}, {area.maxX(), area.maxZ()}};
        for (int[] corner : corners) {
            if (near(corner[0], corner[1], x, z, radius)) {
                columns.add(corner);
            }
        }
        return columns;
    }

    /**
     * @return every column of the claim's outline - its edge blocks, each once - that
     *         falls inside the square of {@code radius} blocks around {@code (x, z)}
     */
    public static List<int[]> outline(ClaimArea area, int x, int z, int radius) {
        List<int[]> columns = new ArrayList<>();
        int fromX = Math.max(area.minX(), x - radius);
        int toX = Math.min(area.maxX(), x + radius);
        int fromZ = Math.max(area.minZ(), z - radius);
        int toZ = Math.min(area.maxZ(), z + radius);
        for (int bx = fromX; bx <= toX; bx++) {
            for (int bz = fromZ; bz <= toZ; bz++) {
                if (bx == area.minX() || bx == area.maxX() || bz == area.minZ() || bz == area.maxZ()) {
                    columns.add(new int[]{bx, bz});
                }
            }
        }
        return columns;
    }

    /**
     * @return how many blocks from {@code (x, z)} to the nearest block of the claim,
     *         measured as a square - {@code 0} inside it. What decides whether a
     *         locked claim's wall is shown at all
     */
    public static int distanceTo(ClaimArea area, int x, int z) {
        int dx = Math.max(0, Math.max(area.minX() - x, x - area.maxX()));
        int dz = Math.max(0, Math.max(area.minZ() - z, z - area.maxZ()));
        return Math.max(dx, dz);
    }

    private static boolean near(int x, int z, int centreX, int centreZ, int radius) {
        return Math.abs(x - centreX) <= radius && Math.abs(z - centreZ) <= radius;
    }

    /**
     * @param every one block in this many is the marker one, counting from the
     *              bottom of the column
     * @return whether this level of a column is the marker block - glowstone in a
     *         glass column, so a pack that clears glass still shows it (the project
     *         owner's report, 22/09/2026)
     */
    public static boolean isMarker(int level, int every) {
        return every > 0 && Math.floorMod(level, every) == 0;
    }
}
