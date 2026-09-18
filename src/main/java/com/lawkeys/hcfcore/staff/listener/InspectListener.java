package com.lawkeys.hcfcore.staff.listener;

import com.lawkeys.hcfcore.staff.DeathSnapshot;
import com.lawkeys.hcfcore.staff.StaffModule;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Keeps a read-only inventory view read-only, and records what players were
 * carrying when they died.
 */
public final class InspectListener implements Listener {

    private final StaffModule module;

    public InspectListener(StaffModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (module.getInvsee().isReadOnlyFor(event.getWhoClicked().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /**
     * A drag is not a click.
     *
     * <p>Its own event, with its own handler list, so cancelling clicks alone leaves
     * a drag across the window working - which is exactly the accident the read-only
     * default exists to prevent.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (module.getInvsee().isReadOnlyFor(event.getWhoClicked().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        module.getInvsee().close(event.getPlayer().getUniqueId());
    }

    /**
     * Ends the sessions of whoever leaves, and those looking at them: the window
     * shows the live inventory of a player who is gone, and what staff changed in it
     * afterwards would never be saved.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID leaving = event.getPlayer().getUniqueId();
        module.getInvsee().close(leaving);
        for (UUID viewerId : module.getInvsee().viewersOf(leaving)) {
            module.getInvsee().close(viewerId);
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null) {
                viewer.closeInventory();
            }
        }
    }

    /**
     * Records what the player was carrying.
     *
     * <p>{@code MONITOR} and read from the inventory rather than from
     * {@code getDrops()}: the drops are what will fall, which is not the same list
     * when another plugin has already edited it, or when the death keeps the
     * inventory. What a check wants to know is what they <em>had</em>.
     *
     * <p>Not for a death another plugin cancels: the player is revived, and an archive
     * of deaths must not list one that did not happen.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        if (!module.getSettings().enabled() || !module.getSettings().lastInventory().enabled()) {
            return;
        }
        Player player = event.getEntity();
        ItemStack[] contents = player.getInventory().getContents();
        byte[] bytes;
        try {
            bytes = ItemStack.serializeItemsAsBytes(contents);
        } catch (RuntimeException e) {
            module.getPlugin().getLogger().log(Level.WARNING,
                    "Could not record the inventory of " + player.getName() + " at death.", e);
            return;
        }
        DeathSnapshot snapshot = new DeathSnapshot(player.getUniqueId(), System.currentTimeMillis(), bytes);
        int keep = module.getSettings().lastInventory().keep();
        Bukkit.getScheduler().runTaskAsynchronously(module.getPlugin(), () -> {
            try {
                module.getLastInventories().save(snapshot, keep);
            } catch (Exception e) {
                module.getPlugin().getLogger().log(Level.WARNING,
                        "Could not archive the death inventory of " + player.getName(), e);
            }
        });
    }
}
