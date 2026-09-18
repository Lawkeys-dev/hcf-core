package com.lawkeys.hcfcore.events.king;

import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.util.ItemText;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Turns a {@link KingKit}'s names into items and effects, and recognises the items
 * afterwards.
 *
 * <p><strong>Every kit item carries a marker</strong> in its persistent data. That
 * is what lets the King keep what they pick up during the reign while the kit is
 * taken back, and what lets the loot dropped at the King's death lose the marker
 * and become ordinary gear for whoever killed them.
 *
 * <p><strong>Lookups read in the Paper 26.2 sources, not assumed</strong>
 * (CONTRIBUTING.md section 6): {@code Registry.MOB_EFFECT} for effects, and
 * {@code RegistryAccess#getRegistry(RegistryKey.ENCHANTMENT)} for enchantments,
 * {@code Registry.ENCHANTMENT} being deprecated since 1.21. Names are wrapped in a
 * {@link NamespacedKey} and looked up with {@code get}, which is what the
 * deprecation note on {@code Registry#match} asks for; the key is lowercased first,
 * because {@code NamespacedKey.fromString} rejects uppercase.
 */
final class KingKitFactory {

    private final NamespacedKey marker;

    KingKitFactory(NamespacedKey marker) {
        this.marker = Objects.requireNonNull(marker, "marker");
    }

    /** @return the effect of that name, or {@code null} if the server has none */
    static PotionEffectType effect(String name) {
        NamespacedKey key = key(name);
        return key == null ? null : Registry.MOB_EFFECT.get(key);
    }

    /** @return the enchantment of that name, or {@code null} if the server has none */
    static Enchantment enchantment(String name) {
        NamespacedKey key = key(name);
        return key == null ? null : RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(key);
    }

    /** @return the item material of that name, or {@code null} if it is not an item */
    static Material material(String name) {
        Material material = Material.matchMaterial(name);
        return material != null && material.isItem() ? material : null;
    }

    private static NamespacedKey key(String name) {
        return name == null ? null : NamespacedKey.fromString(name.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * Equips a player whose inventory has just been emptied. Items that do not
     * fit - a kit longer than the inventory - drop at the player's feet rather than vanish.
     */
    void equip(Player player, KingKit kit) {
        PlayerInventory inventory = player.getInventory();
        for (KingKit.Item spec : kit.items()) {
            ItemStack item = build(spec);
            if (item == null) {
                continue; // reported when the configuration was loaded
            }
            switch (spec.slot()) {
                case HELMET -> inventory.setHelmet(item);
                case CHESTPLATE -> inventory.setChestplate(item);
                case LEGGINGS -> inventory.setLeggings(item);
                case BOOTS -> inventory.setBoots(item);
                case OFF_HAND -> inventory.setItemInOffHand(item);
                case INVENTORY -> {
                    for (ItemStack leftover : inventory.addItem(item).values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                    }
                }
            }
        }
    }

    private ItemStack build(KingKit.Item spec) {
        Material material = material(spec.material());
        if (material == null) {
            return null;
        }
        ItemStack item = ItemStack.of(material, spec.amount());
        for (Map.Entry<String, Integer> entry : spec.enchantments().entrySet()) {
            Enchantment enchantment = enchantment(entry.getKey());
            if (enchantment != null) {
                // Unsafe: the operator may want levels vanilla does not hand out.
                item.addUnsafeEnchantment(enchantment, entry.getValue());
            }
        }
        if (spec.name() != null) {
            item.editMeta(meta -> meta.customName(ItemText.line(LangManager.colorize(spec.name()))));
        }
        item.editPersistentDataContainer(data -> data.set(marker, PersistentDataType.BYTE, (byte) 1));
        return item;
    }

    boolean isKit(ItemStack item) {
        return item != null && !item.isEmpty() && item.getPersistentDataContainer().has(marker);
    }

    /** Makes a kit item ordinary: what the King drops at their death is loot like any other. */
    void unmark(ItemStack item) {
        if (isKit(item)) {
            item.editPersistentDataContainer(data -> data.remove(marker));
        }
    }
}
