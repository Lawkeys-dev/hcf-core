package com.lawkeys.hcfcore.enchant;

import java.util.OptionalInt;

/** The arithmetic of custom enchant levels, pure and tested. */
public final class EnchantLevels {

    private static final int[] VALUES = {10, 9, 5, 4, 1};
    private static final String[] SYMBOLS = {"X", "IX", "V", "IV", "I"};

    private EnchantLevels() {
    }

    /**
     * The level an item ends up with when a book is applied to it - the anvil's rule:
     * a higher book replaces the level, an equal one raises it by one, up to the
     * maximum.
     *
     * @return the new level, or empty when the book would add nothing
     */
    public static OptionalInt merge(int current, int book, int max) {
        if (book < 1) {
            return OptionalInt.empty();
        }
        if (current < book) {
            return OptionalInt.of(Math.min(book, max));
        }
        if (current == book && current < max) {
            return OptionalInt.of(current + 1);
        }
        return OptionalInt.empty();
    }

    /** @return the level in Roman numerals, as the game writes enchantment levels; up to 39 */
    public static String roman(int level) {
        if (level < 1 || level > 39) {
            return String.valueOf(level);
        }
        StringBuilder out = new StringBuilder();
        int rest = level;
        for (int i = 0; i < VALUES.length; i++) {
            while (rest >= VALUES[i]) {
                out.append(SYMBOLS[i]);
                rest -= VALUES[i];
            }
        }
        return out.toString();
    }
}
