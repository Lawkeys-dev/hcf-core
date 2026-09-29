package com.lawkeys.hcfcore.pvp.listener;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/**
 * What the 1.7 combat, removed on 29/09/2026 (a dedicated 1.7/1.8 plugin will follow
 * 1.0), left on items and players: a sword that blocks like a shield, a weapon at its
 * 1.7 damage, a player whose natural regeneration was held off. Taken back from an
 * item when it is seen - a player joining, a slot chosen, a chest opened - and from a
 * player when they join, so a server that ran classic combat plays the game's own.
 */
public final class ClassicLeftovers implements Listener {

    /** The attribute modifier the 1.7 weapon damage was set with. */
    private static final NamespacedKey WEAPON_DAMAGE_KEY =
            Objects.requireNonNull(NamespacedKey.fromString("hcfcore:legacy_damage"));
    /** The regeneration rate that held the game's own off, and the game's defaults. */
    private static final int HELD_OFF_RATE = 1_000_000_000;
    private static final int SATURATED_RATE = 10;
    private static final int UNSATURATED_RATE = 80;

    /** @return whether anything was taken back from this item */
    public static boolean clean(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        boolean changed = false;
        // No sword blocks by default: one that does got it from classic combat.
        if (item.getType().name().endsWith("_SWORD") && item.hasData(DataComponentTypes.BLOCKS_ATTACKS)) {
            item.resetData(DataComponentTypes.BLOCKS_ATTACKS);
            changed = true;
        }
        ItemAttributeModifiers modifiers = item.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (modifiers != null && modifiers.modifiers().stream()
                .anyMatch(entry -> WEAPON_DAMAGE_KEY.equals(entry.modifier().getKey()))) {
            item.resetData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
            changed = true;
        }
        return changed;
    }

    private static void clean(Inventory inventory) {
        for (ItemStack item : inventory.getContents()) {
            clean(item);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        clean(player.getInventory());
        if (player.getSaturatedRegenRate() == HELD_OFF_RATE) {
            player.setSaturatedRegenRate(SATURATED_RATE);
        }
        if (player.getUnsaturatedRegenRate() == HELD_OFF_RATE) {
            player.setUnsaturatedRegenRate(UNSATURATED_RATE);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onHeld(PlayerItemHeldEvent event) {
        clean(event.getPlayer().getInventory().getItem(event.getNewSlot()));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onOpen(InventoryOpenEvent event) {
        clean(event.getInventory());
    }
}
