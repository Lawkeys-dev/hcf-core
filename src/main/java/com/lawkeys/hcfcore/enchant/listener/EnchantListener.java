package com.lawkeys.hcfcore.enchant.listener;

import com.lawkeys.hcfcore.enchant.CustomEnchant;
import com.lawkeys.hcfcore.enchant.EnchantKind;
import com.lawkeys.hcfcore.enchant.EnchantLevels;
import com.lawkeys.hcfcore.enchant.EnchantMessages;
import com.lawkeys.hcfcore.enchant.EnchantModule;
import com.lawkeys.hcfcore.lang.LangManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * Where custom enchants act on the world: a hit taken (Hellforged, Recover), a block
 * broken (Autosmelt), and a book dropped onto an item.
 */
public final class EnchantListener implements Listener {

    private final EnchantModule module;

    public EnchantListener(EnchantModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    /** {@code MONITOR}: only a hit that really lands repairs anything or brings Recover. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            module.onHurt(player, player.getHealth() - event.getFinalDamage());
        }
    }

    /**
     * Autosmelt: the drops of a block broken with an Autosmelt tool come out as a
     * furnace would make them. The drop list is documented as mutable (26.2), and each
     * drop keeps its entity - only what it holds changes.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrops(BlockDropItemEvent event) {
        if (!module.isEnabled()) {
            return;
        }
        ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        boolean smelts = module.levels(tool).keySet().stream()
                .anyMatch(enchant -> enchant.kind() == EnchantKind.AUTOSMELT && EnchantModule.fits(enchant, tool));
        if (!smelts) {
            return;
        }
        for (Item drop : event.getItems()) {
            module.smelted(drop.getItemStack()).ifPresent(drop::setItemStack);
        }
    }

    /**
     * A book on the cursor, clicked onto an item in the player's own inventory.
     *
     * <p>Only onto an item it goes on: anything else is an ordinary click, so a book
     * can still be swapped into a slot like any item. Not in creative, where the
     * client, not the server, decides what the cursor holds.
     *
     * <p>The click is cancelled and the change made a tick later: the javadoc of
     * {@code InventoryClickEvent} warns that slots the click touches can be
     * overwritten, and its {@code setCursor} is deprecated for changing the cursor
     * "before any calculations". Everything is read again then, so a book or an item
     * moved in between is never lost or duplicated.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !module.isEnabled()
                || player.getGameMode() == GameMode.CREATIVE
                || event.getClickedInventory() == null
                || event.getClickedInventory().getType() != InventoryType.PLAYER) {
            return;
        }
        var book = module.bookOf(event.getCursor());
        ItemStack target = event.getCurrentItem();
        if (book.isEmpty() || !EnchantModule.fits(book.get().enchant(), target)) {
            return;
        }
        event.setCancelled(true);
        int slot = event.getSlot();
        Bukkit.getScheduler().runTask(module.getPlugin(), () -> apply(player, slot));
    }

    private void apply(Player player, int slot) {
        ItemStack cursor = player.getItemOnCursor();
        ItemStack target = player.getInventory().getItem(slot);
        var book = module.bookOf(cursor);
        if (book.isEmpty() || target == null || target.isEmpty()) {
            return;
        }
        CustomEnchant enchant = book.get().enchant();
        if (!EnchantModule.fits(enchant, target)) {
            module.getLang().send(player, EnchantMessages.BOOK_WRONG_ITEM,
                    "enchant", LangManager.colorize(enchant.displayName()));
            return;
        }
        OptionalInt level = EnchantLevels.merge(module.levels(target).getOrDefault(enchant, 0),
                book.get().level(), enchant.maxLevel());
        if (level.isEmpty()) {
            module.getLang().send(player, EnchantMessages.BOOK_NOTHING_TO_ADD,
                    "enchant", LangManager.colorize(enchant.displayName()));
            return;
        }
        module.set(target, enchant, level.getAsInt());
        player.getInventory().setItem(slot, target);
        cursor.setAmount(cursor.getAmount() - 1);
        player.setItemOnCursor(cursor.getAmount() > 0 ? cursor : null);
        module.getLang().send(player, EnchantMessages.BOOK_APPLIED,
                "enchant", LangManager.colorize(enchant.displayName()), "level", EnchantLevels.roman(level.getAsInt()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        module.forget(event.getPlayer().getUniqueId());
    }
}
