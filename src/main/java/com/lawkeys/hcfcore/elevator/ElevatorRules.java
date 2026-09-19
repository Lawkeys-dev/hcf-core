package com.lawkeys.hcfcore.elevator;

import java.util.OptionalInt;
import java.util.function.IntPredicate;

/**
 * Where an elevator sign takes a player: the next floor up or down its column. Pure,
 * the world asked through two questions.
 *
 * <p><strong>Linked elevators first</strong> (the project owner's rule, 19/09/2026):
 * another elevator sign up or down the same column is where the player goes, whatever
 * lies between - no pillar of blocks needed. Only a sign alone in its column looks for
 * a floor: a height whose block below stands firm and whose two blocks are free for a
 * player to stand in.
 *
 * <p><strong>Everything is measured from the sign, never from the rider</strong>: a
 * player standing a block below an {@code Up} sign once had the floor under the sign
 * found for them - the sign itself. The floor a sign serves is the one whose rider
 * reads it at their feet or at eye level; that floor never counts. Either search goes no further than {@code maxDistance} blocks.
 */
public final class ElevatorRules {

    /** Which way a sign goes. */
    public enum Direction { UP, DOWN }

    private ElevatorRules() {
    }

    /**
     * The next elevator sign that way in the column, if there is one.
     *
     * @param signY      the height of the sign clicked
     * @param isElevator whether the block at a height is an elevator sign
     * @return that sign's height
     */
    public static OptionalInt linkedSign(int signY, int minY, int maxY, Direction direction, int maxDistance,
                                         IntPredicate isElevator) {
        int step = direction == Direction.UP ? 1 : -1;
        int limit = maxDistance <= 0 ? Integer.MAX_VALUE : maxDistance;
        for (int y = signY + step, travelled = 1; travelled <= limit && y >= minY && y < maxY; y += step, travelled++) {
            if (isElevator.test(y)) {
                return OptionalInt.of(y);
            }
        }
        return OptionalInt.empty();
    }

    /**
     * Where the rider's feet go at a linked sign: in front of it with the sign at eye
     * level, or at their feet - the first with two free blocks, preferring one with
     * ground under it. Only the sign decides, not where the rider stood.
     *
     * @return the height, or empty for no room in front of the sign
     */
    public static OptionalInt arrivalAtLinked(int targetSignY, IntPredicate firm, IntPredicate free) {
        int[] candidates = {targetSignY - 1, targetSignY};
        for (int y : candidates) {
            if (firm.test(y - 1) && free.test(y) && free.test(y + 1)) {
                return OptionalInt.of(y);
            }
        }
        for (int y : candidates) {
            if (free.test(y) && free.test(y + 1)) {
                return OptionalInt.of(y);
            }
        }
        return OptionalInt.empty();
    }

    /**
     * The height a floor search starts from: the floor a sign serves never counts.
     * Going up, anything above the sign; going down, anything below the floor of a
     * rider who reads it at eye level.
     */
    public static int searchFrom(int signY, Direction direction) {
        return direction == Direction.UP ? signY : signY - 1;
    }

    /**
     * @param feetY       the height the search starts from, never counted itself;
     *                    {@link #searchFrom} for a sign
     * @param minY        the world's lowest height
     * @param maxY        the world's highest height, exclusive
     * @param maxDistance how far to look, in blocks; {@code 0} for the whole column
     * @param firm        whether the block at a height can be stood on
     * @param free        whether a player can stand in the block at a height
     * @return the height to stand at, or empty for no floor that way
     */
    public static OptionalInt destination(int feetY, int minY, int maxY, Direction direction, int maxDistance,
                                          IntPredicate firm, IntPredicate free) {
        int step = direction == Direction.UP ? 1 : -1;
        int limit = maxDistance <= 0 ? Integer.MAX_VALUE : maxDistance;
        for (int y = feetY + step, travelled = 1; travelled <= limit; y += step, travelled++) {
            if (y - 1 < minY || y + 1 >= maxY) {
                break;
            }
            if (firm.test(y - 1) && free.test(y) && free.test(y + 1)) {
                return OptionalInt.of(y);
            }
        }
        return OptionalInt.empty();
    }
}
