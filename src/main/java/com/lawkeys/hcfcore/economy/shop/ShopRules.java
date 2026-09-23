package com.lawkeys.hcfcore.economy.shop;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * {@code economy.yml}, {@code shop}: where players buy and sell for money - the
 * spawn shop of HCF servers, and the money that flows in from the ores they mine
 * (the project owner's request, 23/09/2026). Through signs, a menu, or both.
 */
public record ShopRules(boolean enabled, Mode mode, String buyHeader, String sellHeader, List<Item> items,
                        List<Category> categories) {

    /**
     * A shelf of the menu: {@code /shop} lists the categories, a click opens one.
     *
     * @param name what players read, colour tokens included
     * @param icon the item standing for it
     */
    public record Category(String id, String name, String icon, List<Item> items) {

        public Category {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(icon, "icon");
            items = List.copyOf(Objects.requireNonNull(items, "items"));
        }
    }

    /** Where the shop is: signs placed by staff, the {@code /shop} menu, or both. */
    public enum Mode {
        SIGNS, MENU, BOTH;

        public boolean signs() {
            return this != MENU;
        }

        public boolean menu() {
            return this != SIGNS;
        }

        public static Optional<Mode> of(String raw) {
            for (Mode mode : values()) {
                if (mode.name().equalsIgnoreCase(raw == null ? "" : raw.trim())) {
                    return Optional.of(mode);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * One line of the menu: {@code amount} of {@code material} for {@code buy} or
     * {@code sell}; either at 0 is not offered.
     */
    public record Item(String material, int amount, double buy, double sell) {

        public Item {
            Objects.requireNonNull(material, "material");
            amount = Math.max(1, Math.min(64, amount));
            buy = Double.isFinite(buy) ? Math.max(0.0, buy) : 0.0;
            sell = Double.isFinite(sell) ? Math.max(0.0, sell) : 0.0;
        }

        public boolean buyable() {
            return buy > 0;
        }

        public boolean sellable() {
            return sell > 0;
        }
    }

    public ShopRules {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(buyHeader, "buyHeader");
        Objects.requireNonNull(sellHeader, "sellHeader");
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        categories = List.copyOf(Objects.requireNonNull(categories, "categories"));
    }

    /** A shop without categories: one list, as it was before they existed. */
    public ShopRules(boolean enabled, Mode mode, String buyHeader, String sellHeader, List<Item> items) {
        this(enabled, mode, buyHeader, sellHeader, items, List.of());
    }

    public static ShopRules defaults() {
        return new ShopRules(true, Mode.BOTH, "[Buy]", "[Sell]", List.of(), List.of());
    }

    /** @return whether the menu has anything on its shelves */
    public boolean hasItems() {
        return !items.isEmpty() || categories.stream().anyMatch(category -> !category.items().isEmpty());
    }

    public static ShopRules load(ConfigurationSection section, Consumer<String> warn) {
        ShopRules d = defaults();
        if (section == null) {
            return d;
        }
        String modeName = section.getString("mode", "both");
        Mode mode = Mode.of(modeName).orElseGet(() -> {
            warn.accept("shop.mode '" + modeName + "' is not signs, menu or both; both is used.");
            return Mode.BOTH;
        });
        ConfigurationSection signs = section.getConfigurationSection("signs");
        String buy = signs == null ? d.buyHeader() : signs.getString("buy-header", d.buyHeader());
        String sell = signs == null ? d.sellHeader() : signs.getString("sell-header", d.sellHeader());
        List<Category> categories = new ArrayList<>();
        ConfigurationSection shelves = section.getConfigurationSection("menu.categories");
        if (shelves != null) {
            for (String id : shelves.getKeys(false)) {
                ConfigurationSection shelf = shelves.getConfigurationSection(id);
                if (shelf == null) {
                    continue;
                }
                String icon = shelf.getString("icon", "CHEST");
                Material iconMaterial = icon == null ? null : Material.matchMaterial(icon);
                if (iconMaterial == null || !iconMaterial.isItem()) {
                    warn.accept("shop.menu.categories." + id + ".icon '" + icon + "' is not an item; CHEST is used.");
                    icon = "CHEST";
                }
                List<Item> onShelf = items(shelf.getMapList("items"), "shop.menu.categories." + id + ".items", warn);
                if (onShelf.isEmpty()) {
                    warn.accept("shop.menu.categories." + id + " has no item; left out.");
                    continue;
                }
                categories.add(new Category(id, shelf.getString("name", id), icon.toUpperCase(Locale.ROOT), onShelf));
            }
        }
        List<Item> items = items(section.getMapList("menu.items"), "shop.menu.items", warn);
        return new ShopRules(section.getBoolean("enabled", d.enabled()), mode,
                buy == null ? d.buyHeader() : buy.trim(), sell == null ? d.sellHeader() : sell.trim(), items,
                categories);
    }

    private static List<Item> items(List<Map<?, ?>> rawItems, String where, Consumer<String> warn) {
        List<Item> items = new ArrayList<>();
        for (Map<?, ?> raw : rawItems) {
            Object name = raw.get("material");
            Material material = name == null ? null : Material.matchMaterial(name.toString());
            if (material == null || !material.isItem()) {
                warn.accept(where + ": '" + name + "' is not an item; left out.");
                continue;
            }
            Item item = new Item(material.name(), number(raw.get("amount"), 1).intValue(),
                    number(raw.get("buy"), 0).doubleValue(), number(raw.get("sell"), 0).doubleValue());
            if (!item.buyable() && !item.sellable()) {
                warn.accept(where + ": " + material.name() + " has neither a buy nor a sell price; left out.");
                continue;
            }
            items.add(item);
        }
        return items;
    }

    private static Number number(Object raw, Number fallback) {
        if (raw instanceof Number number) {
            return number;
        }
        try {
            return raw == null ? fallback : Double.parseDouble(raw.toString().trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** @return {@code DIAMOND_ORE} as {@code Diamond Ore} */
    public static String readable(String material) {
        StringBuilder name = new StringBuilder();
        for (String word : material.toLowerCase(Locale.ROOT).split("_")) {
            if (!word.isEmpty()) {
                name.append(name.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1));
            }
        }
        return name.toString();
    }
}
