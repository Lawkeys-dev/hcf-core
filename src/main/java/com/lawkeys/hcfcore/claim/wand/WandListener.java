package com.lawkeys.hcfcore.claim.wand;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Objects;

/**
 * The wand's clicks. Every click with it is taken - it neither tills the ground nor
 * breaks anything - and the wand never leaves its holder: dropped, it gives the
 * selection up and vanishes; it cannot be put in a chest; it is not dropped on death.
 */
public final class WandListener implements Listener {

    private final WandSessions sessions;

    public WandListener(WandSessions sessions) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !sessions.getWand().isWand(event.getItem())) {
            return;
        }
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        Player player = event.getPlayer();
        Action action = event.getAction();
        boolean left = action == Action.LEFT_CLICK_BLOCK || action == Action.LEFT_CLICK_AIR;
        if (left && player.isSneaking()) {
            sessions.confirm(player);
        } else if (action == Action.LEFT_CLICK_BLOCK && event.getClickedBlock() != null) {
            sessions.select(player, 1, event.getClickedBlock());
        } else if (action == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            sessions.select(player, 2, event.getClickedBlock());
        }
    }

    /** A creative player's left click breaks at once: not with the wand. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent event) {
        if (sessions.getWand().isWand(event.getPlayer().getInventory().getItemInMainHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (sessions.getWand().isWand(event.getItemDrop().getItemStack())) {
            event.getItemDrop().remove();
            sessions.end(event.getPlayer(), true);
        }
    }

    /** Kept out of every inventory but the holder's own. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getType() == InventoryType.CRAFTING) {
            return;
        }
        if (sessions.getWand().isWand(event.getCurrentItem()) || sessions.getWand().isWand(event.getCursor())
                || (event.getHotbarButton() >= 0 && sessions.getWand().isWand(
                event.getWhoClicked().getInventory().getItem(event.getHotbarButton())))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(item -> sessions.getWand().isWand(item));
        sessions.forget(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        sessions.end(event.getPlayer(), false);
    }
}
