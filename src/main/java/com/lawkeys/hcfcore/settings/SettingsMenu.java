package com.lawkeys.hcfcore.settings;

import com.lawkeys.hcfcore.util.ItemText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final SettingsModule module;
    private final Player viewer;
    private final List<PlayerSetting> shown;
    private final Inventory inventory;

    private SettingsMenu(SettingsModule module, Player viewer) {
        this.module = module;
        this.viewer = viewer;
        this.shown = module.offered();
        int size = Math.max(9, (shown.size() + 8) / 9 * 9);
        this.inventory = Bukkit.createInventory(this, size,
                LEGACY.deserialize(module.getLang().get(SettingsMessages.MENU_TITLE)));
        redraw();
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
        return rawSlot >= 0 && rawSlot < shown.size() ? shown.get(rawSlot) : null;
    }

    public Player viewer() {
        return viewer;
    }

    /** Draws every item again from the settings as they are now. */
    public void redraw() {
        for (int slot = 0; slot < shown.size(); slot++) {
            inventory.setItem(slot, icon(shown.get(slot)));
        }
    }

    private ItemStack icon(PlayerSetting setting) {
        boolean on = module.isOn(viewer, setting);
        ItemStack item = ItemStack.of(on ? Material.LIME_DYE : Material.GRAY_DYE);
        String state = module.getLang().get(on ? SettingsMessages.STATE_ON : SettingsMessages.STATE_OFF);
        List<Component> lore = new ArrayList<>();
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
