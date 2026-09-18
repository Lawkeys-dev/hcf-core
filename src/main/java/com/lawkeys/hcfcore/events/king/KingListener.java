package com.lawkeys.hcfcore.events.king;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.UUID;

/**
 * Everything the King does that Kill the King has to answer: where they may go,
 * what they may do with the kit, and how the reign ends.
 *
 * <p><strong>Spawn is shut to the King, by any means</strong> (decided by the
 * project owner on 11/09/2026): walking or flying in, an ender pearl, a chorus
 * fruit, a command or plugin teleport, a portal, and vehicles, which the King may
 * not mount at all.
 *
 * <p><strong>Handler lists, checked in the Paper 26.2 sources.</strong> Bukkit
 * hands an event to the listeners of the class that was fired, and a subclass
 * that declares its own {@code HandlerList} is not seen by its parent's
 * listeners. Among the events below, {@code PlayerTeleportEvent},
 * {@code PlayerPortalEvent} and {@code PlayerArmorStandManipulateEvent} each have
 * their own list, so each has its own handler here;
 * {@code PlayerTeleportEndGatewayEvent} and {@code PlayerInteractAtEntityEvent}
 * share their parent's and are caught there. Leaving a safe zone stays possible,
 * in case the King ended up in one; and should anything slip through, the rule
 * engine already counts a safe zone as outside, so hiding there withers them.
 *
 * <p><strong>The kit stays on the King.</strong> Their own items are given back
 * at the end, so a kit they could hand to their team - dropped, put in a chest,
 * hung in an item frame, given to an allay - would leave the event with the team
 * and the King's items back with them. While King, a player can drop nothing,
 * open no container, interact with no entity and put nothing in a block that holds
 * items (a pot, a shelf). What drops at the King's death is the kit, and that is
 * the point.
 */
final class KingListener implements Listener {

    private final KingEventController controller;

    KingListener(KingEventController controller) {
        this.controller = Objects.requireNonNull(controller, "controller");
    }

    private boolean isKing(Player player) {
        KingEventManager manager = controller.getManager();
        return manager != null && manager.isKing(player.getUniqueId());
    }

    // ------------------------------------------------------------------
    // Spawn is shut
    // ------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock() || !isKing(event.getPlayer())) {
            return;
        }
        if (entersSafeZone(event.getFrom(), event.getTo())) {
            event.setCancelled(true);
            controller.refuse(event.getPlayer(), KingMessages.SAFE_ZONE_REFUSED);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onTeleport(PlayerTeleportEvent event) {
        refuseSafeDestination(event);
    }

    /**
     * A {@code PlayerTeleportEvent} subclass with a handler list of its own: the
     * handler above never sees it (Bukkit dispatches on the fired class's list).
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPortal(PlayerPortalEvent event) {
        refuseSafeDestination(event);
    }

    private void refuseSafeDestination(PlayerTeleportEvent event) {
        if (isKing(event.getPlayer()) && event.getTo() != null && controller.isSafeZone(event.getTo())) {
            event.setCancelled(true);
            controller.refuse(event.getPlayer(), KingMessages.SAFE_ZONE_REFUSED);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onMount(EntityMountEvent event) {
        if (event.getEntity() instanceof Player player && isKing(player)) {
            event.setCancelled(true);
            controller.refuse(player, KingMessages.NOT_WHILE_KING);
        }
    }

    private boolean entersSafeZone(Location from, Location to) {
        return to != null && controller.isSafeZone(to) && !controller.isSafeZone(from);
    }

    // ------------------------------------------------------------------
    // The kit stays on the King
    // ------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onDrop(PlayerDropItemEvent event) {
        if (isKing(event.getPlayer())) {
            event.setCancelled(true);
            controller.refuse(event.getPlayer(), KingMessages.NOT_WHILE_KING);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && isKing(player)) {
            event.setCancelled(true);
            controller.refuse(player, KingMessages.NOT_WHILE_KING);
        }
    }

    /**
     * Item frames, allays, animals with chests: all ways to hand an item over.
     * {@code PlayerInteractAtEntityEvent} shares this handler list and arrives here
     * too; the armour stand event does not - see below.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        refuseInteraction(event);
    }

    /** A {@code PlayerInteractEntityEvent} subclass with a handler list of its own. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        refuseInteraction(event);
    }

    private void refuseInteraction(PlayerInteractEntityEvent event) {
        if (isKing(event.getPlayer())) {
            event.setCancelled(true);
            controller.refuse(event.getPlayer(), KingMessages.NOT_WHILE_KING);
        }
    }

    /**
     * Blocks that take an item from the hand with no window to open: a decorated pot,
     * a shelf (which swaps with the hotbar), a chiseled bookshelf, a lectern, a
     * jukebox, a campfire, a flower pot. The container rule above never sees them,
     * so the King could leave the kit in a pot for their team to break (noted in the
     * listener review, 14/09/2026).
     *
     * <p>Only the block is refused, as territory protection does: the King still
     * eats, drinks and throws, whatever they are looking at.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onUseBlock(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null
                || !takesItems(block.getType()) || !isKing(event.getPlayer())) {
            return;
        }
        event.setUseInteractedBlock(Event.Result.DENY);
        controller.refuse(event.getPlayer(), KingMessages.NOT_WHILE_KING);
    }

    /** Names and tags checked against Paper 26.2. */
    private static boolean takesItems(Material type) {
        return type == Material.DECORATED_POT || type == Material.CHISELED_BOOKSHELF
                || type == Material.LECTERN || type == Material.JUKEBOX
                || Tag.WOODEN_SHELVES.isTagged(type) || Tag.CAMPFIRES.isTagged(type)
                || Tag.FLOWER_POTS.isTagged(type);
    }

    // ------------------------------------------------------------------
    // How the reign ends
    // ------------------------------------------------------------------

    // HIGHEST and not MONITOR: the drops are still being edited here. A death
    // another plugin cancels is not the King's death, hence ignoreCancelled.
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player king = event.getEntity();
        boolean reigning = isKing(king);
        // A King who logged out in combat is no longer King when pvp/ kills them,
        // but still carries the kit: their drops are loot all the same.
        if (reigning || controller.isOwed(king.getUniqueId())) {
            for (ItemStack drop : event.getDrops()) {
                controller.getKits().unmark(drop);
            }
        }
        if (!reigning) {
            return;
        }
        Player killer = king.getKiller();
        UUID killerId = killer == null ? null : killer.getUniqueId();
        controller.getManager()
                .kingDied(killerId, controller.teamOf(killerId), controller.teamOf(king.getUniqueId()))
                .ifPresent(controller::handle);
    }

    // LOWEST: ahead of pvp/, which kills a combat-logger at MONITOR. Leaving ends
    // the reign with no winner, whatever happens to the player after.
    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        if (isKing(event.getPlayer())) {
            controller.getManager().kingQuit().ifPresent(controller::handle);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (controller.isOwed(player.getUniqueId())) {
            // A tick later, once the join has finished loading their inventory.
            Bukkit.getScheduler().runTask(controller.getPlugin(), () -> {
                if (player.isOnline()) {
                    controller.restore(player);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerPostRespawnEvent event) {
        if (controller.isOwed(event.getPlayer().getUniqueId())) {
            controller.restore(event.getPlayer());
        }
    }
}
