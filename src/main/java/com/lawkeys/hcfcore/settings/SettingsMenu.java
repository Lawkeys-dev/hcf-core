package com.lawkeys.hcfcore.settings;

import com.lawkeys.hcfcore.theme.MenuLayout;
import com.lawkeys.hcfcore.theme.MenuStyle;
import com.lawkeys.hcfcore.util.ItemText;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The {@code /settings} menu: one item per setting, green when on, grey when off;
 * a click flips it.
 *
 * <p>This class is the inventory's {@link InventoryHolder}, which is how the click
 * listener tells the menu apart from every other window on the server.
 */
public final class SettingsMenu implements InventoryHolder {

    private final SettingsModule module;
    private final Player viewer;
    private final List<PlayerSetting> shown;
    private final MenuLayout layout;
    private final Inventory inventory;

    private SettingsMenu(SettingsModule module, Player viewer) {
        this.module = module;
        this.viewer = viewer;
        this.shown = module.offered();
        this.layout = MenuStyle.layout(shown.size(), 0);
        this.inventory = Bukkit.createInventory(this, layout.size(),
                MenuStyle.title(module.getLang().get(SettingsMessages.MENU_TITLE)));
        redraw();
        MenuStyle.decorate(inventory, layout, module.getLang());
    }

    public static void open(SettingsModule module, Player viewer) {
        Objects.requireNonNull(module, "module");
        viewer.openInventory(new SettingsMenu(module, viewer).getInventory());
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /** @return the setting shown in this slot, or {@code null} for none */
    public PlayerSetting settingAt(int rawSlot) {
        int index = layout.itemSlots().indexOf(rawSlot);
        return index >= 0 && index < shown.size() ? shown.get(index) : null;
    }

    public Player viewer() {
        return viewer;
    }

    /** Draws every item again from the settings as they are now. */
    public void redraw() {
        for (int i = 0; i < shown.size() && i < layout.itemSlots().size(); i++) {
            inventory.setItem(layout.itemSlots().get(i), icon(shown.get(i)));
        }
    }

    private ItemStack icon(PlayerSetting setting) {
        boolean on = module.isOn(viewer, setting);
        ItemStack item = ItemStack.of(on ? Material.LIME_DYE : Material.GRAY_DYE);
        String state = module.getLang().get(on ? SettingsMessages.STATE_ON : SettingsMessages.STATE_OFF);
        List<Component> lore = new ArrayList<>();
        Component separator = MenuStyle.separator(module.getLang());
        if (separator != null) {
            lore.add(separator);
        }
        lore.add(ItemText.line(module.getLang().get(SettingsMessages.description(setting))));
        lore.add(Component.empty());
        lore.add(ItemText.line(module.getLang().get(SettingsMessages.LORE_STATE, "state", state)));
        lore.add(ItemText.line(module.getLang().get(SettingsMessages.LORE_CLICK)));
        item.editMeta(meta -> {
            meta.customName(ItemText.line(module.displayName(setting)));
            meta.lore(lore);
        });
        return item;
    }
}
