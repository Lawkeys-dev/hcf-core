package com.lawkeys.hcfcore.enchant;

import java.util.Objects;
import java.util.Set;

/**
 * One custom enchant, as {@code enchants.yml} defines it.
 *
 * @param displayName what the lore shows before the level, colour codes included
 * @param effect      for {@link EnchantKind#EFFECT}: the effect's key ({@code speed})
 * @param amount      for {@link EnchantKind#HELLFORGED}: durability restored per level on
 *                    each hit; for {@link EnchantKind#IMPLANTED}: food points restored
 *                    per level; for {@link EnchantKind#RECOVER}: the health below which
 *                    it fires (20 is full)
 * @param intervalSeconds for {@link EnchantKind#IMPLANTED}: how often
 * @param durationSeconds for {@link EnchantKind#RECOVER}: how long the regeneration lasts
 * @param cooldownSeconds for {@link EnchantKind#RECOVER}: how long before it can fire again
 */
public record CustomEnchant(String id, String displayName, EnchantKind kind, int maxLevel,
                            Set<EnchantTarget> targets, String effect, double amount,
                            long intervalSeconds, long durationSeconds, long cooldownSeconds) {

    public CustomEnchant {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(kind, "kind");
        targets = Set.copyOf(targets);
        if (maxLevel < 1) {
            throw new IllegalArgumentException("max-level must be at least 1");
        }
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("a custom enchant must go on something");
        }
    }

    public boolean fits(EnchantTarget target) {
        return targets.contains(target);
    }

    /** @return whether it only does anything on a piece being worn */
    public boolean isWorn() {
        return kind != EnchantKind.AUTOSMELT;
    }
}
