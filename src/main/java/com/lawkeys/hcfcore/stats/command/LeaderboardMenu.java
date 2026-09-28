package com.lawkeys.hcfcore.stats.command;

import com.lawkeys.hcfcore.stats.StatsMessages;
import com.lawkeys.hcfcore.stats.StatsModule;
import com.lawkeys.hcfcore.theme.MenuLayout;
import com.lawkeys.hcfcore.theme.MenuStyle;
import com.lawkeys.hcfcore.util.ItemText;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /leaderboard} as a window (the owner's request of 28/09/2026): one item per
 * board - kills, deaths, K/D, killstreak, playtime, and the teams' kills and points -
 * each listing its top in its lines, so every ranking reads at a glance.
 */
public final class LeaderboardMenu implements InventoryHolder {

    private final Inventory inventory;

    private LeaderboardMenu(StatsModule module) {
        List<String> boards = TopCommand.KINDS;
        MenuLayout layout = MenuStyle.layout(boards.size(), 0);
        this.inventory = Bukkit.createInventory(this, layout.size(),
                MenuStyle.title(module.getLang().get(StatsMessages.MENU_TITLE)));
        int rows = module.getLeaderboardMenu().rows();
        for (int i = 0; i < boards.size() && i < layout.itemSlots().size(); i++) {
            inventory.setItem(layout.itemSlots().get(i), board(module, boards.get(i), rows));
        }
        MenuStyle.decorate(inventory, layout, module.getLang());
    }

    public static void open(StatsModule module, Player viewer) {
        viewer.openInventory(new LeaderboardMenu(module).getInventory());
    }

    private static ItemStack board(StatsModule module, String kind, int rows) {
        var lang = module.getLang();
        Material material = Material.matchMaterial(module.getLeaderboardMenu().icon(kind));
        ItemStack item = ItemStack.of(material == null || !material.isItem() ? Material.PAPER : material);
        List<Component> lore = new ArrayList<>();
        Component separator = MenuStyle.separator(lang);
        if (separator != null) {
            lore.add(separator);
        }
        List<String[]> top = TopCommand.rows(module, kind, rows);
        if (top.isEmpty()) {
            lore.add(ItemText.line(lang.get(StatsMessages.MENU_EMPTY)));
        }
        for (int rank = 0; rank < top.size(); rank++) {
            lore.add(ItemText.line(lang.get(StatsMessages.MENU_LINE, "rank", String.valueOf(rank + 1),
                    "name", top.get(rank)[0], "value", top.get(rank)[1])));
        }
        item.editMeta(meta -> {
            meta.customName(ItemText.line(lang.get(StatsMessages.MENU_BOARD,
                    "board", lang.get(StatsMessages.board(kind)))));
            meta.lore(lore);
        });
        return item;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /** Nothing to take: a window to read. */
    public static final class Clicks implements Listener {

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onClick(InventoryClickEvent event) {
            if (event.getInventory().getHolder(false) instanceof LeaderboardMenu) {
                event.setCancelled(true);
            }
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onDrag(InventoryDragEvent event) {
            if (event.getInventory().getHolder(false) instanceof LeaderboardMenu) {
                event.setCancelled(true);
            }
        }
    }
}
