package com.lawkeys.hcfcore.ability;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/** The rules of the abilities that need no server. */
public final class AbilityRules {

    private AbilityRules() {
    }

    /**
     * Lucky Mode: what a hit is multiplied by, somewhere from {@code minPercent} to
     * {@code maxPercent} more - a negative percent is less.
     *
     * @param random from 0 (inclusive) to 1 (exclusive)
     */
    public static double luckyMultiplier(double minPercent, double maxPercent, double random) {
        double low = Math.min(minPercent, maxPercent);
        double high = Math.max(minPercent, maxPercent);
        return Math.max(0.0, 1.0 + (low + (high - low) * random) / 100.0);
    }

    /**
     * Magic Rock: the effects for this many free blocks above a player's head; none
     * when the table has no line for it.
     */
    public static List<AbilityEffect> magicRock(Map<Integer, List<AbilityEffect>> table, int freeBlocks) {
        return table.getOrDefault(freeBlocks, List.of());
    }

    /**
     * Anti-build: whether a block is one a player under it may not open. An entry
     * that is a block's own name ({@code CHEST}) is that block alone - not
     * {@code TRAPPED_CHEST}; one that is no block's name is the end of a family's
     * ({@code FENCE_GATE} covers {@code OAK_FENCE_GATE}, {@code TRAPDOOR} every
     * trapdoor).
     *
     * @param isBlockName whether a name, upper case, is a block's own
     */
    public static boolean blocked(List<String> entries, String material, Predicate<String> isBlockName) {
        String name = material.toUpperCase(Locale.ROOT);
        for (String entry : entries) {
            String wanted = entry.trim().toUpperCase(Locale.ROOT);
            if (name.equals(wanted) || (!isBlockName.test(wanted) && name.endsWith("_" + wanted))) {
                return true;
            }
        }
        return false;
    }

    /** Thunderbolt and the others: whether a roll of {@code random} (0 to 1) falls within {@code percent}. */
    public static boolean chance(double percent, double random) {
        return random * 100.0 < percent;
    }
}
