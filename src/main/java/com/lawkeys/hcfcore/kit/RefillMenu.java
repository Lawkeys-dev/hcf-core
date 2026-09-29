package com.lawkeys.hcfcore.kit;

import com.lawkeys.hcfcore.theme.MenuStyle;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Objects;

/**
 * A {@code [Refill]} sign's window: a kit's items in self-service - potions, pearls,
 * a Rogue's golden swords - for a player back at spawn. What is taken is put back
 * at once, so the window never runs dry; nothing can be put into it, and nothing
 * thrown out of it ({@code listener/RefillMenuListener}).
 */
public final class RefillMenu implements InventoryHolder {

    private final Inventory inventory;
    private final ItemStack[] stock;

    private RefillMenu(String title, ItemStack[] items, int rows) {
        ItemStack[] held;
        if (items.length > ChestKits.SIZE) {
            // A double chest's kit: shown as it was laid out.
            held = Arrays.stream(items).map(item -> item == null || item.isEmpty() ? null : item.clone())
                    .toArray(ItemStack[]::new);
        } else {
            held = Arrays.stream(items).filter(item -> item != null && !item.isEmpty())
                    .map(ItemStack::clone).toArray(ItemStack[]::new);
        }
        rows = Math.max(1, Math.min(6, rows));
        this.stock = Arrays.copyOf(held, Math.min(held.length, rows * 9));
        this.inventory = Bukkit.createInventory(this, rows * 9, MenuStyle.title(title));
        inventory.setContents(Arrays.copyOf(stock, rows * 9));
    }

    /** Opens the window on a kit's items, under that title, that many rows high. */
    public static void open(org.bukkit.entity.Player player, String title, ItemStack[] items, int rows) {
        Objects.requireNonNull(items, "items");
        player.openInventory(new RefillMenu(title, items, rows).getInventory());
    }

    /** Puts back whatever was taken: the window is never emptied. */
    public void restock() {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, slot < stock.length && stock[slot] != null ? stock[slot].clone() : null);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
