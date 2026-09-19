package com.lawkeys.hcfcore.ability;

import com.lawkeys.hcfcore.theme.MenuLayout;
import com.lawkeys.hcfcore.theme.MenuStyle;
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

    private final AbilityModule module;
    private final MenuLayout layout;
    private final Inventory inventory;

    public AbilityMenu(AbilityModule module, Player viewer) {
        this(module, viewer, 0);
    }

    public AbilityMenu(AbilityModule module, Player viewer, int page) {
        this.module = module;
        AbilitySettings settings = module.getSettings();
        List<Ability> abilities = settings.abilities();
        this.layout = MenuStyle.layout(abilities.size(), page);
        this.inventory = Bukkit.createInventory(this, layout.size(), MenuStyle.title(settings.menuTitle()));
        long now = System.currentTimeMillis();
        var lang = module.getLang();
        for (int i = 0; i < layout.itemSlots().size(); i++) {
            Ability ability = abilities.get(layout.firstItem() + i);
            ItemStack icon = module.buildItem(ability, 1);
            long left = module.getCooldowns().remaining(viewer.getUniqueId(), ability.id(), now);
            icon.editMeta(meta -> {
                List<Component> lore = new ArrayList<>();
                Component separator = MenuStyle.separator(lang);
                if (separator != null) {
                    lore.add(separator);
                }
                if (meta.lore() != null) {
                    lore.addAll(meta.lore());
                }
                lore.add(Component.empty());
                lore.add(ItemText.line(lang.get(AbilityMessages.MENU_TYPE, "type", ability.type().configName())));
                lore.add(ItemText.line(left > 0
                        ? lang.get(AbilityMessages.MENU_COOLDOWN, "time", Durations.formatWithSeconds(left))
                        : lang.get(AbilityMessages.MENU_READY)));
                meta.lore(lore);
            });
            inventory.setItem(layout.itemSlots().get(i), icon);
        }
        MenuStyle.decorate(inventory, layout, lang);
    }

    /** @return the page this slot's arrow leads to, or {@code -1} if it holds none */
    public int pageAt(int rawSlot) {
        if (rawSlot >= 0 && rawSlot == layout.previous()) {
            return layout.page() - 1;
        }
        return rawSlot >= 0 && rawSlot == layout.next() ? layout.page() + 1 : -1;
    }

    /** Opens another page of this menu. */
    public void turnTo(Player viewer, int page) {
        viewer.openInventory(new AbilityMenu(module, viewer, page).getInventory());
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
