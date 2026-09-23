package com.lawkeys.hcfcore.economy.shop;

import com.lawkeys.hcfcore.economy.EconomyManager;
import com.lawkeys.hcfcore.economy.EconomyMessages;
import com.lawkeys.hcfcore.lang.LangManager;
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
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /shop}: every item of {@code shop.menu.items}, a page at a time. Left
 * click buys one lot, sneaking a stack's worth; right click sells one lot,
 * sneaking everything held.
 */
public final class ShopMenu implements InventoryHolder {

    private final List<ShopRules.Item> items;
    private final MenuLayout layout;
    private final Inventory inventory;

    private ShopMenu(ShopRules rules, EconomyManager money, LangManager lang, int page) {
        this.items = rules.items();
        this.layout = MenuStyle.layout(items.size(), page);
        this.inventory = Bukkit.createInventory(this, layout.size(), MenuStyle.title(lang.get(EconomyMessages.SHOP_MENU_TITLE)));
        for (int i = 0; i < layout.itemSlots().size() && layout.firstItem() + i < items.size(); i++) {
            inventory.setItem(layout.itemSlots().get(i), icon(items.get(layout.firstItem() + i), money, lang));
        }
        MenuStyle.decorate(inventory, layout, lang);
    }

    public static void open(Player player, ShopRules rules, EconomyManager money, LangManager lang, int page) {
        player.openInventory(new ShopMenu(rules, money, lang, page).getInventory());
    }

    private static ItemStack icon(ShopRules.Item item, EconomyManager money, LangManager lang) {
        Material material = Material.matchMaterial(item.material());
        ItemStack icon = new ItemStack(material == null ? Material.BARRIER : material, item.amount());
        List<Component> lore = new ArrayList<>();
        Component separator = MenuStyle.separator(lang);
        if (separator != null) {
            lore.add(separator);
        }
        lore.add(ItemText.line(item.buyable()
                ? lang.get(EconomyMessages.SHOP_LORE_BUY, "price", format(money, item.buy()))
                : lang.get(EconomyMessages.SHOP_LORE_NOT_BUYABLE)));
        lore.add(ItemText.line(item.sellable()
                ? lang.get(EconomyMessages.SHOP_LORE_SELL, "price", format(money, item.sell()))
                : lang.get(EconomyMessages.SHOP_LORE_NOT_SELLABLE)));
        lore.add(Component.empty());
        lore.add(ItemText.line(lang.get(EconomyMessages.SHOP_LORE_HINT)));
        icon.editMeta(meta -> {
            meta.customName(ItemText.line(lang.get(EconomyMessages.SHOP_ITEM_NAME,
                    "amount", String.valueOf(item.amount()), "item", ShopRules.readable(item.material()))));
            meta.lore(lore);
        });
        return icon;
    }

    private static String format(EconomyManager money, double amount) {
        return money == null ? String.valueOf(amount) : money.format(amount);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /** The menu's clicks: a trade, a page turn, or nothing - never an item taken. */
    public static final class Clicks implements Listener {

        private final java.util.function.Supplier<ShopRules> rules;
        private final java.util.function.Supplier<EconomyManager> economy;
        private final LangManager lang;
        private final Shop shop;

        public Clicks(java.util.function.Supplier<ShopRules> rules, java.util.function.Supplier<EconomyManager> economy,
                      LangManager lang, Shop shop) {
            this.rules = rules;
            this.economy = economy;
            this.lang = lang;
            this.shop = shop;
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onClick(InventoryClickEvent event) {
            if (!(event.getInventory().getHolder(false) instanceof ShopMenu menu)) {
                return;
            }
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != event.getInventory()) {
                return;
            }
            int slot = event.getRawSlot();
            if (slot == menu.layout.previous() || slot == menu.layout.next()) {
                int page = menu.layout.page() + (slot == menu.layout.next() ? 1 : -1);
                open(player, rules.get(), economy.get(), lang, page);
                return;
            }
            int index = menu.layout.itemSlots().indexOf(slot);
            if (index < 0 || menu.layout.firstItem() + index >= menu.items.size()) {
                return;
            }
            ShopRules.Item item = menu.items.get(menu.layout.firstItem() + index);
            Material material = Material.matchMaterial(item.material());
            if (material == null) {
                return;
            }
            ClickType click = event.getClick();
            if (click.isLeftClick()) {
                if (!item.buyable()) {
                    lang.send(player, EconomyMessages.SHOP_NOT_BUYABLE);
                    return;
                }
                int lots = click.isShiftClick() ? Math.max(1, material.getMaxStackSize() / item.amount()) : 1;
                shop.buy(player, material, item.amount(), item.buy(), lots);
            } else if (click.isRightClick()) {
                if (!item.sellable()) {
                    lang.send(player, EconomyMessages.SHOP_NOT_SELLABLE);
                    return;
                }
                shop.sell(player, material, item.amount(), item.sell(), click.isShiftClick());
            }
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onDrag(InventoryDragEvent event) {
            if (event.getInventory().getHolder(false) instanceof ShopMenu) {
                event.setCancelled(true);
            }
        }
    }
}
