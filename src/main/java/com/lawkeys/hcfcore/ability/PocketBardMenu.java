package com.lawkeys.hcfcore.ability;

import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.theme.MenuStyle;
import com.lawkeys.hcfcore.util.ItemText;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The Pocket Bard's menu: one icon per set ({@code pocket-bard.items}); a click picks
 * it. Read-only - its items never leave it.
 */
public final class PocketBardMenu implements InventoryHolder {

    private final Ability pocketBard;
    private final Inventory inventory;
    private final Map<Integer, PocketBardItem> bySlot = new HashMap<>();

    public PocketBardMenu(AbilityModule module, Ability pocketBard) {
        this.pocketBard = Objects.requireNonNull(pocketBard, "pocketBard");
        AbilitySettings settings = module.getSettings();
        this.inventory = Bukkit.createInventory(this, settings.pocketBardSize(),
                MenuStyle.title(settings.pocketBardTitle()));
        for (PocketBardItem item : settings.pocketBard()) {
            Material material = Objects.requireNonNull(Material.matchMaterial(item.material()));
            ItemStack icon = ItemStack.of(material, item.amount());
            icon.editMeta(meta -> {
                meta.customName(ItemText.line(LangManager.colorize(item.menuName())));
                if (!item.lore().isEmpty()) {
                    meta.lore(item.lore().stream().map(line -> ItemText.line(LangManager.colorize(line))).toList());
                }
            });
            inventory.setItem(item.slot(), icon);
            bySlot.put(item.slot(), item);
        }
        // The sets sit where the file says; the frame goes around them.
        MenuStyle.decorate(inventory, module.getLang());
    }

    public Ability pocketBard() {
        return pocketBard;
    }

    public Optional<PocketBardItem> at(int slot) {
        return Optional.ofNullable(bySlot.get(slot));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
