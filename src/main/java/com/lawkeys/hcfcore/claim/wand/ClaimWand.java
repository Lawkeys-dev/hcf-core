package com.lawkeys.hcfcore.claim.wand;

import com.lawkeys.hcfcore.claim.ClaimSettings;
import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.util.ItemText;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

/**
 * The claiming wand as an item: made from {@code claims.yml} ({@code wand}), and told
 * apart from every other hoe by a mark in its data, never by its name or type.
 */
public final class ClaimWand {

    private final NamespacedKey key;

    public ClaimWand(Plugin plugin) {
        this.key = new NamespacedKey(Objects.requireNonNull(plugin, "plugin"), "claim_wand");
    }

    public ItemStack create(ClaimSettings.WandRules rules) {
        Material material = Material.matchMaterial(rules.material());
        ItemStack item = new ItemStack(material == null ? Material.GOLDEN_HOE : material);
        ItemMeta meta = item.getItemMeta();
        meta.customName(ItemText.line(LangManager.colorize(rules.name())));
        meta.lore(rules.lore().stream().map(line -> ItemText.line(LangManager.colorize(line))).toList());
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isWand(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }
}
