package com.lawkeys.hcfcore.limiter;

import java.util.Objects;

/**
 * Immutable snapshot of {@code limiters.yml}.
 *
 * @param enchantments     the highest level of each enchantment
 * @param fixExistingItems also bring down items that arrive some other way - loot,
 *                         trades, fishing, another plugin - rather than only those
 *                         made at an enchanting table or an anvil
 * @param potions          the highest level each effect may be applied at from a
 *                         potion
 */
public record LimiterSettings(boolean enabled, LevelCaps enchantments, boolean fixExistingItems,
                              LevelCaps potions) {

    public LimiterSettings {
        Objects.requireNonNull(enchantments, "enchantments");
        Objects.requireNonNull(potions, "potions");
    }

    /**
     * Built-in fallback, mirroring {@code resources/limiters.yml}: the mechanism on,
     * and no caps. Which levels an HCF map allows - Protection I or II, Sharpness I or
     * II - is the operator's call, not a default to invent.
     */
    public static LimiterSettings defaults() {
        return new LimiterSettings(true, LevelCaps.none(), true, LevelCaps.none());
    }
}
