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
import java.util.function.Supplier;

/**
 * {@code /shop}. With categories ({@code shop.menu.categories}) it opens on the
 * shelves, a click opening one, with a button back; without, on the one list of
 * {@code shop.menu.items}. In a list, left click buys one lot, sneaking a stack's
 * worth; right click sells one lot, sneaking everything held.
 */
public final class ShopMenu implements InventoryHolder {

    private static final int COLUMNS = 9;

    /** The shelves, when this page shows them; empty on a page of items. */
    private final List<ShopRules.Category> categories;
    /** The items of this page's list; empty on the page of shelves. */
    private final List<ShopRules.Item> items;
    /** The shelf this list is, or {@code -1} for the shelves or a shop without any. */
    private final int category;
    private final MenuLayout layout;
    /** The button back to the shelves, or {@code -1}. */
    private final int back;
    private final Inventory inventory;

    private ShopMenu(ShopRules rules, EconomyManager money, LangManager lang, int requested, int page) {
        // A shelf opened before a reload that took it away: back to the shelves.
        int category = requested < rules.categories().size() ? requested : -1;
        boolean shelves = category < 0 && !rules.categories().isEmpty();
        this.categories = shelves ? rules.categories() : List.of();
        this.category = category;
        this.items = shelves ? List.of()
                : category >= 0 ? rules.categories().get(category).items() : rules.items();
        this.layout = MenuStyle.layout(shelves ? categories.size() : items.size(), page);

        int size = layout.size();
        int backSlot = -1;
        if (category >= 0) {
            int bottomMiddle = (layout.rows() - 1) * COLUMNS + 4;
            if (layout.frameSlots().contains(bottomMiddle) || layout.pages() > 1) {
                // The frame's bottom bar, or the row of a paged list's arrows.
                backSlot = bottomMiddle;
            } else if (layout.rows() < 6) {
                // No frame to hold it: a row of its own.
                size += COLUMNS;
                backSlot = layout.rows() * COLUMNS + 4;
            }
            // Six full rows and no frame: no room for the button - /shop again goes back.
        }
        this.back = backSlot;
        String title = category >= 0
                ? lang.get(EconomyMessages.SHOP_CATEGORY_TITLE, "category", rules.categories().get(category).name())
                : lang.get(EconomyMessages.SHOP_MENU_TITLE);
        this.inventory = Bukkit.createInventory(this, size, MenuStyle.title(title));

        for (int i = 0; i < layout.itemSlots().size(); i++) {
            int index = layout.firstItem() + i;
            if (shelves && index < categories.size()) {
                inventory.setItem(layout.itemSlots().get(i), shelf(categories.get(index), lang));
            } else if (!shelves && index < items.size()) {
                inventory.setItem(layout.itemSlots().get(i), icon(items.get(index), money, lang));
            }
        }
        if (back >= 0) {
            ItemStack button = ItemStack.of(Material.ARROW);
            button.editMeta(meta -> meta.customName(ItemText.line(lang.get(EconomyMessages.SHOP_BACK))));
            inventory.setItem(back, button);
        }
        MenuStyle.decorate(inventory, layout, lang);
    }

    /** Opens the shop: on its shelves, or its one list when it has no shelves. */
    public static void open(Player player, ShopRules rules, EconomyManager money, LangManager lang) {
        player.openInventory(new ShopMenu(rules, money, lang, -1, 0).getInventory());
    }

    private static void open(Player player, ShopRules rules, EconomyManager money, LangManager lang,
                             int category, int page) {
        player.openInventory(new ShopMenu(rules, money, lang, category, page).getInventory());
    }

    private static ItemStack shelf(ShopRules.Category category, LangManager lang) {
        Material material = Material.matchMaterial(category.icon());
        ItemStack icon = ItemStack.of(material == null ? Material.CHEST : material);
        List<Component> lore = new ArrayList<>();
        Component separator = MenuStyle.separator(lang);
        if (separator != null) {
            lore.add(separator);
        }
        lore.add(ItemText.line(lang.get(EconomyMessages.SHOP_CATEGORY_LORE,
                "count", String.valueOf(category.items().size()))));
        lore.add(Component.empty());
        lore.add(ItemText.line(lang.get(EconomyMessages.SHOP_CATEGORY_HINT)));
        icon.editMeta(meta -> {
            meta.customName(ItemText.line(lang.get(EconomyMessages.SHOP_CATEGORY_NAME, "category", category.name())));
            meta.lore(lore);
        });
        return icon;
    }

    private static ItemStack icon(ShopRules.Item item, EconomyManager money, LangManager lang) {
        Material material = Material.matchMaterial(item.material());
        ItemStack icon = new ItemStack(material == null ? Material.BARRIER : material,
                Math.min(item.amount(), material == null ? 1 : material.getMaxStackSize()));
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
                    "amount", String.valueOf(item.amount()), "item", com.lawkeys.hcfcore.util.MaterialNames.readable(item.material()))));
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

    /** The menu's clicks: a shelf opened, a trade, a page turned, back - never an item taken. */
    public static final class Clicks implements Listener {

        private final Supplier<ShopRules> rules;
        private final Supplier<EconomyManager> economy;
        private final LangManager lang;
        private final Shop shop;

        public Clicks(Supplier<ShopRules> rules, Supplier<EconomyManager> economy, LangManager lang, Shop shop) {
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
            ShopRules current = rules.get();
            int slot = event.getRawSlot();
            if (slot == menu.back) {
                open(player, current, economy.get(), lang);
                return;
            }
            if (slot == menu.layout.previous() || slot == menu.layout.next()) {
                int page = menu.layout.page() + (slot == menu.layout.next() ? 1 : -1);
                open(player, current, economy.get(), lang, menu.category, page);
                return;
            }
            int index = menu.layout.itemSlots().indexOf(slot);
            if (index < 0) {
                return;
            }
            index += menu.layout.firstItem();
            if (!menu.categories.isEmpty()) {
                // The shelves as the reader saw them: a reload since may have changed them.
                if (index < current.categories().size()) {
                    open(player, current, economy.get(), lang, index, 0);
                }
                return;
            }
            if (index >= menu.items.size()) {
                return;
            }
            trade(player, menu.items.get(index), event.getClick());
        }

        private void trade(Player player, ShopRules.Item item, ClickType click) {
            Material material = Material.matchMaterial(item.material());
            if (material == null) {
                return;
            }
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
