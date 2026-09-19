package com.lawkeys.hcfcore.theme;

import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.util.ItemText;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * The theme's menus ({@code theme.yml}, {@code menus}): their titles, their frame of
 * panes, their page arrows, the line under an item's name. Every menu of the plugin
 * goes through here, so they all look alike and change together.
 */
public final class MenuStyle {

    private MenuStyle() {
    }

    /** A menu title: the theme's small capitals, coloured. */
    public static Component title(String raw) {
        return ItemText.line(LangManager.colorize(LangManager.theme().title(raw)));
    }

    /** Where {@code count} items go on {@code page}, in the theme's frame. */
    public static MenuLayout layout(int count, int page) {
        return MenuLayout.of(count, LangManager.theme().menus().frame(), page);
    }

    /**
     * Fills the frame's slots that are still empty with panes, and puts the page arrows
     * in: a menu placing its own items first keeps them, the frame going around them.
     */
    public static void decorate(Inventory inventory, MenuLayout layout, LangManager lang) {
        Theme.Menus menus = LangManager.theme().menus();
        for (int slot : layout.frameSlots()) {
            if (slot < inventory.getSize() && isEmpty(inventory.getItem(slot))) {
                inventory.setItem(slot, pane(layout.cornerSlots().contains(slot) ? menus.cornerPane() : menus.pane()));
            }
        }
        String page = lang.get(MenuMessages.PAGE, "page", String.valueOf(layout.page() + 1),
                "pages", String.valueOf(layout.pages()));
        if (layout.previous() >= 0) {
            inventory.setItem(layout.previous(), button(Material.ARROW, lang.get(MenuMessages.PREVIOUS), page));
        }
        if (layout.next() >= 0) {
            inventory.setItem(layout.next(), button(Material.ARROW, lang.get(MenuMessages.NEXT), page));
        }
    }

    /** Frames a menu whose items sit where its configuration says: every empty frame slot of a menu this size. */
    public static void decorate(Inventory inventory, LangManager lang) {
        int rows = inventory.getSize() / 9;
        MenuLayout frame = MenuLayout.of(Math.max(0, (rows - 2) * 7), LangManager.theme().menus().frame(), 0);
        if (frame.rows() == rows) {
            decorate(inventory, frame, lang);
        }
    }

    /** The line under an item's name, before its description; {@code null} when the language file leaves it empty. */
    public static Component separator(LangManager lang) {
        String line = lang.get(MenuMessages.SEPARATOR);
        return line == null || line.isEmpty() ? null : ItemText.line(line);
    }

    /** A pane of the frame, or any filler: no name, no tooltip. */
    public static ItemStack pane(String materialName) {
        Material material = Material.matchMaterial(materialName);
        ItemStack pane = ItemStack.of(material == null || !material.isItem() ? Material.BLACK_STAINED_GLASS_PANE : material);
        pane.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().hideTooltip(true).build());
        return pane;
    }

    private static ItemStack button(Material material, String name, String lore) {
        ItemStack item = ItemStack.of(material);
        item.editMeta(meta -> {
            meta.customName(ItemText.line(name));
            meta.lore(List.of(ItemText.line(lore)));
        });
        return item;
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.isEmpty();
    }
}
