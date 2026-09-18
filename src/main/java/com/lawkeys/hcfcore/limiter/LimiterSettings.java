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
 * @param effects          the highest level each effect may have on a player,
 *                         whatever gives it
 */
public record LimiterSettings(boolean enabled, LevelCaps enchantments, boolean fixExistingItems,
                              LevelCaps potions, LevelCaps effects) {

    public LimiterSettings {
        Objects.requireNonNull(enchantments, "enchantments");
        Objects.requireNonNull(potions, "potions");
        Objects.requireNonNull(effects, "effects");
    }

    /**
     * The level an effect may be given at: its effect cap, whatever gives it, and
     * its potion cap too when a potion does. Never raised.
     *
     * @param key        the effect's key, {@code minecraft:resistance}
     * @param fromPotion drunk, splashed, a lingering cloud, a tipped arrow
     * @return the level allowed; {@code 0} means refused
     */
    public int allowedLevel(String key, int level, boolean fromPotion) {
        if (!enabled) {
            return level;
        }
        int allowed = effects.clamp(key, level);
        return fromPotion ? Math.min(allowed, potions.clamp(key, level)) : allowed;
    }

    /**
     * Built-in fallback, mirroring {@code resources/limiters.yml}: the mechanism on,
     * and no caps. Which levels an HCF map allows - Protection I or II, Sharpness I or
     * II - is the operator's call, not a default to invent.
     */
    public static LimiterSettings defaults() {
        return new LimiterSettings(true, LevelCaps.none(), true, LevelCaps.none(), LevelCaps.none());
    }
}
