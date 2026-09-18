package com.lawkeys.hcfcore.events.king;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What the King is given for the length of the reign, as read from
 * {@code events.yml}.
 *
 * <p>Plain data - names, not server objects - so that a definition stays pure
 * and testable; the server layer turns the names into items and effects, and
 * the loader has already reported and dropped any name the server does not know.
 *
 * @param items   the items, each in the slot it goes to
 * @param effects effect key (as in {@code minecraft:speed}, namespace optional) to
 *                level as players see it (1 is Speed I), in configuration order
 */
public record KingKit(List<Item> items, Map<String, Integer> effects) {

    public KingKit {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        effects = java.util.Collections.unmodifiableMap(
                new LinkedHashMap<>(Objects.requireNonNull(effects, "effects")));
    }

    public static KingKit empty() {
        return new KingKit(List.of(), Map.of());
    }

    public enum Slot {
        HELMET, CHESTPLATE, LEGGINGS, BOOTS, OFF_HAND,
        /** Anywhere in the main inventory, in configuration order. */
        INVENTORY
    }

    /**
     * @param material     the material name, as the server spells it
     * @param amount       stack size, at least 1
     * @param enchantments enchantment key to level
     * @param name         custom name with colour codes, or {@code null} for the
     *                     item's own name
     */
    public record Item(Slot slot, String material, int amount, Map<String, Integer> enchantments, String name) {

        public Item {
            Objects.requireNonNull(slot, "slot");
            Objects.requireNonNull(material, "material");
            if (amount < 1) {
                throw new IllegalArgumentException("amount must be at least 1: " + amount);
            }
            enchantments = java.util.Collections.unmodifiableMap(
                    new LinkedHashMap<>(Objects.requireNonNull(enchantments, "enchantments")));
        }
    }
}
