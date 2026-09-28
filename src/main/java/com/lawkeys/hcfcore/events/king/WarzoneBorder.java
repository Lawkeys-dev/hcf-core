package com.lawkeys.hcfcore.events.king;

import java.util.ArrayList;
import java.util.List;

/**
 * The border of a warzone square near a point: what the King is shown of the edge
 * they may not cross, as a safe zone's wall is shown to a player in combat. Pure.
 */
final class WarzoneBorder {

    private WarzoneBorder() {
    }

    /**
     * @param centerX the warzone's centre
     * @param radius  the warzone's radius: its border is the square's edge, at
     *                {@code center ± radius}
     * @param near    how far from the point, horizontally, a column may be
     * @return the border columns within {@code near} of {@code (x, z)}, as {x, z} pairs,
     *         each once
     */
    static List<int[]> columnsNear(int centerX, int centerZ, int radius, int x, int z, int near) {
        List<int[]> columns = new ArrayList<>();
        int minX = centerX - radius;
        int maxX = centerX + radius;
        int minZ = centerZ - radius;
        int maxZ = centerZ + radius;
        for (int edgeX : new int[] {minX, maxX}) {
            if (Math.abs(edgeX - x) > near) {
                continue;
            }
            for (int cz = Math.max(minZ, z - near); cz <= Math.min(maxZ, z + near); cz++) {
                add(columns, edgeX, cz, x, z, near);
            }
        }
        for (int edgeZ : new int[] {minZ, maxZ}) {
            if (Math.abs(edgeZ - z) > near) {
                continue;
            }
            for (int cx = Math.max(minX + 1, x - near); cx <= Math.min(maxX - 1, x + near); cx++) {
                add(columns, cx, edgeZ, x, z, near);
            }
        }
        return columns;
    }

    private static void add(List<int[]> columns, int cx, int cz, int x, int z, int near) {
        long dx = cx - x;
        long dz = cz - z;
        if (dx * dx + dz * dz <= (long) near * near) {
            columns.add(new int[] {cx, cz});
        }
    }
}
