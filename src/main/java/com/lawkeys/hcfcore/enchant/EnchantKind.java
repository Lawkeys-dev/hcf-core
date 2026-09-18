package com.lawkeys.hcfcore.enchant;

/** What a custom enchant does - the well-known HCF and kitmap enchants, by behaviour. */
public enum EnchantKind {
    /** A potion effect while the piece is worn: Fire Resistance, Speed, Night Vision... */
    EFFECT,
    /** Repairs the worn piece a little every time its wearer takes damage. */
    HELLFORGED,
    /** Keeps its wearer fed: hunger is restored every few seconds. */
    IMPLANTED,
    /** Regeneration when the wearer drops below a health threshold, then a cooldown. */
    RECOVER,
    /** A pickaxe that smelts what it mines, as a furnace would. */
    AUTOSMELT
}
