package com.lawkeys.hcfcore.limiter;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Reading and writing the enchantment levels of an item, books included.
 *
 * <p>An enchanted book keeps its enchantments as <em>stored</em> enchantments - the
 * ones it will give - not as its own, so every read and write has to ask which kind
 * of item it is holding. That question lives here, once.
 */
public final class ItemLevels {

    private ItemLevels() {
    }

    /** @return the item's enchantments, or a book's stored ones; empty for nothing */
    public static Map<Enchantment, Integer> of(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return Map.of();
        }
        if (item.getItemMeta() instanceof EnchantmentStorageMeta book) {
            return book.getStoredEnchants();
        }
        return item.getEnchantments();
    }

    /** Sets one level, {@code 0} removing the enchantment. */
    public static void set(ItemStack item, Enchantment enchantment, int level) {
        if (item.getItemMeta() instanceof EnchantmentStorageMeta) {
            item.editMeta(EnchantmentStorageMeta.class, book -> {
                book.removeStoredEnchant(enchantment);
                if (level > 0) {
                    book.addStoredEnchant(enchantment, level, true);
                }
            });
            return;
        }
        item.removeEnchantment(enchantment);
        if (level > 0) {
            item.addUnsafeEnchantment(enchantment, level);
        }
    }

    /**
     * Brings every enchantment of an item down to its cap. Books are left alone:
     * they only give their levels through an anvil, which applies the caps itself.
     *
     * @return whether anything changed
     */
    public static boolean clampEquipment(ItemStack item, LevelCaps caps) {
        if (item == null || item.isEmpty() || item.getItemMeta() instanceof EnchantmentStorageMeta) {
            return false;
        }
        boolean changed = false;
        for (Map.Entry<Enchantment, Integer> entry : new HashMap<>(item.getEnchantments()).entrySet()) {
            int allowed = caps.clamp(LimiterModule.key(entry.getKey()), entry.getValue());
            if (allowed != entry.getValue()) {
                set(item, entry.getKey(), allowed);
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Whether an anvil result gives nothing the input did not already have: the same
     * enchantments, the same wear and the same name. Taking such a result would cost
     * experience - and the sacrificed item - for nothing.
     */
    public static boolean gainsNothing(ItemStack input, ItemStack result) {
        if (input == null || input.isEmpty() || input.getType() != result.getType()) {
            return false;
        }
        if (!of(input).equals(of(result))) {
            return false;
        }
        ItemMeta before = input.getItemMeta();
        ItemMeta after = result.getItemMeta();
        if (before == null || after == null) {
            return before == after;
        }
        int wearBefore = before instanceof Damageable damageable ? damageable.getDamage() : 0;
        int wearAfter = after instanceof Damageable damageable ? damageable.getDamage() : 0;
        return wearBefore == wearAfter && Objects.equals(before.customName(), after.customName());
    }
}
