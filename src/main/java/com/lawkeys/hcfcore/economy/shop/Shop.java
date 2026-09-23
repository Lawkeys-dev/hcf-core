package com.lawkeys.hcfcore.economy.shop;

import com.lawkeys.hcfcore.economy.EconomyManager;
import com.lawkeys.hcfcore.economy.EconomyMessages;
import com.lawkeys.hcfcore.lang.LangManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Buying and selling, whatever the shop's face - a sign or the menu. Only plain
 * items are counted and taken: a renamed or enchanted stack is somebody's gear,
 * never sold by mistake.
 */
public final class Shop {

    private final Supplier<EconomyManager> economy;
    private final LangManager lang;

    public Shop(Supplier<EconomyManager> economy, LangManager lang) {
        this.economy = Objects.requireNonNull(economy, "economy");
        this.lang = Objects.requireNonNull(lang, "lang");
    }

    /** @param lotsWanted how many lots of {@code lotSize} to buy, each at {@code price} */
    public void buy(Player player, Material material, int lotSize, double price, int lotsWanted) {
        EconomyManager money = economy.get();
        if (money == null) {
            lang.send(player, EconomyMessages.DISABLED);
            return;
        }
        double balance = money.getBalance(player.getUniqueId());
        int lots = ShopMath.lotsToBuy(lotsWanted, lotSize, price, balance, room(player.getInventory(), material));
        String item = ShopRules.readable(material.name());
        if (lots == 0) {
            lang.send(player, balance < price ? EconomyMessages.SHOP_NO_MONEY : EconomyMessages.SHOP_NO_ROOM,
                    "price", money.format(price), "amount", String.valueOf(lotSize), "item", item);
            return;
        }
        double total = price * lots;
        if (!money.withdraw(player.getUniqueId(), total).isOk()) {
            lang.send(player, EconomyMessages.SHOP_NO_MONEY, "price", money.format(total),
                    "amount", String.valueOf(lotSize), "item", item);
            return;
        }
        int count = lotSize * lots;
        give(player, material, count);
        lang.send(player, EconomyMessages.SHOP_BOUGHT, "amount", String.valueOf(count), "item", item,
                "price", money.format(total));
    }

    /** @param all sell everything held in whole lots, rather than one lot */
    public void sell(Player player, Material material, int lotSize, double price, boolean all) {
        EconomyManager money = economy.get();
        if (money == null) {
            lang.send(player, EconomyMessages.DISABLED);
            return;
        }
        int lots = ShopMath.lotsToSell(held(player.getInventory(), material), lotSize, all);
        String item = ShopRules.readable(material.name());
        if (lots == 0) {
            lang.send(player, EconomyMessages.SHOP_NOTHING_TO_SELL, "amount", String.valueOf(lotSize), "item", item);
            return;
        }
        int count = lotSize * lots;
        double total = price * lots;
        player.getInventory().removeItem(new ItemStack(material, count));
        if (!money.deposit(player.getUniqueId(), total).isOk()) {
            // The balance is at its ceiling: the items come back, nothing is lost.
            give(player, material, count);
            lang.send(player, EconomyMessages.ABOVE_MAXIMUM);
            return;
        }
        lang.send(player, EconomyMessages.SHOP_SOLD, "amount", String.valueOf(count), "item", item,
                "price", money.format(total));
    }

    private static boolean plain(ItemStack stack, Material material) {
        return stack != null && stack.isSimilar(new ItemStack(material));
    }

    private static int held(PlayerInventory inventory, Material material) {
        int held = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            if (plain(stack, material)) {
                held += stack.getAmount();
            }
        }
        return held;
    }

    private static int room(PlayerInventory inventory, Material material) {
        int room = 0;
        int max = material.getMaxStackSize();
        for (ItemStack stack : inventory.getStorageContents()) {
            if (stack == null || stack.getType().isAir()) {
                room += max;
            } else if (plain(stack, material)) {
                room += Math.max(0, max - stack.getAmount());
            }
        }
        return room;
    }

    private static void give(Player player, Material material, int count) {
        int max = material.getMaxStackSize();
        while (count > 0) {
            int stack = Math.min(max, count);
            for (ItemStack left : player.getInventory().addItem(new ItemStack(material, stack)).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
            count -= stack;
        }
    }
}
