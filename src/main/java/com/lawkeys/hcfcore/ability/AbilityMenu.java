package com.lawkeys.hcfcore.ability;

import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.util.Durations;
import com.lawkeys.hcfcore.util.ItemText;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /ability}: every ability this server runs, as its item, with the viewer's
 * cooldown on it. Read-only.
 */
public final class AbilityMenu implements InventoryHolder {

    private final Inventory inventory;

    public AbilityMenu(AbilityModule module, Player viewer) {
        AbilitySettings settings = module.getSettings();
        int size = Math.max(9, Math.min(54, (settings.abilities().size() + 8) / 9 * 9));
        this.inventory = Bukkit.createInventory(this, size, ItemText.line(LangManager.colorize(settings.menuTitle())));
        long now = System.currentTimeMillis();
        var lang = module.getLang();
        int slot = 0;
        for (Ability ability : settings.abilities()) {
            if (slot >= size) {
                break;
            }
            ItemStack icon = module.buildItem(ability, 1);
            long left = module.getCooldowns().remaining(viewer.getUniqueId(), ability.id(), now);
            icon.editMeta(meta -> {
                List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
                lore.add(Component.empty());
                lore.add(ItemText.line(lang.get(AbilityMessages.MENU_TYPE, "type", ability.type().configName())));
                lore.add(ItemText.line(left > 0
                        ? lang.get(AbilityMessages.MENU_COOLDOWN, "time", Durations.formatWithSeconds(left))
                        : lang.get(AbilityMessages.MENU_READY)));
                meta.lore(lore);
            });
            inventory.setItem(slot++, icon);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
