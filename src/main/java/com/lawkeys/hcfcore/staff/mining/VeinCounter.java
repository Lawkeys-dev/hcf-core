package com.lawkeys.hcfcore.staff.mining;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The vein a mined ore belongs to: every block of the same ore touching it, face to
 * face or edge to edge, found by a flood fill. An alert says "found 8 diamonds" once
 * for the whole vein, rather than eight times for its eight blocks.
 *
 * <p>Pure Java: the world is a predicate, so it is unit-tested without one.
 */
public final class VeinCounter {

    /** A block position. */
    public record Position(int x, int y, int z) {
    }

    private VeinCounter() {
    }

    /**
     * @param isSameOre whether that position holds the same ore as the one mined
     * @param max       the most blocks to walk: a vein larger than this is reported
     *                  at this size, and a pathological cave of ore cannot stall the
     *                  server
     * @return every block of the vein, the mined one first
     */
    public static Set<Position> vein(Position start, Predicate<Position> isSameOre, int max) {
        Objects.requireNonNull(start, "start");
        Set<Position> found = new LinkedHashSet<>();
        Deque<Position> queue = new ArrayDeque<>();
        found.add(start);
        queue.add(start);
        while (!queue.isEmpty() && found.size() < max) {
            Position at = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        // Face and edge neighbours only: two ores meeting at a single
                        // corner are two veins in the game's own generation.
                        int touching = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                        if (touching == 0 || touching == 3) {
                            continue;
                        }
                        Position next = new Position(at.x() + dx, at.y() + dy, at.z() + dz);
                        if (found.size() < max && !found.contains(next) && isSameOre.test(next)) {
                            found.add(next);
                            queue.add(next);
                        }
                    }
                }
            }
        }
        return found;
    }
}
