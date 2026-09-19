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
 * player to stand in, the search starting from the player's feet - their own floor
 * never counts. Either search goes no further than {@code maxDistance} blocks.
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
     * Where the player's feet go at a linked sign: as high above or below it as they
     * stood from the sign they clicked, within one block - a sign read at eye level
     * is found at eye level on arrival.
     */
    public static int feetAtLinked(int clickedSignY, int feetY, int targetSignY) {
        int offset = Math.max(0, Math.min(1, clickedSignY - feetY));
        return targetSignY - offset;
    }

    /**
     * @param feetY       the height of the player's feet
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
