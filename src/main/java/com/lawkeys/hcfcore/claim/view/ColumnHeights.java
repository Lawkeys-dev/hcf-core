package com.lawkeys.hcfcore.claim.view;

/**
 * How high a column of the claim views rises: from the ground to a fixed level -
 * layer 128 as shipped, the project owner's choice of 22/09/2026, so a pillar is
 * seen from far away and from above rather than only from beside it.
 *
 * <p>Pure Java: unit-tested without a server.
 */
public final class ColumnHeights {

    private ColumnHeights() {
    }

    /**
     * @param ground    the highest block of the column, which the drawing starts above
     * @param topY      the level to rise to
     * @param minimum   how many blocks to draw when the ground is already at or above
     *                  {@code topY} - a base on a mountain still gets a column
     * @param worldMax  the world's build limit, never drawn through
     * @return {@code {fromY, toY}}, both included, or {@code null} when there is no
     *         room at all
     */
    public static int[] range(int ground, int topY, int minimum, int worldMax) {
        int from = ground + 1;
        if (from >= worldMax) {
            return null;
        }
        int to = Math.max(topY, ground + Math.max(1, minimum));
        to = Math.min(to, worldMax - 1);
        return to < from ? null : new int[]{from, to};
    }
}
