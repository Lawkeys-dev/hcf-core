package com.lawkeys.hcfcore.settings.listener;

import com.lawkeys.hcfcore.settings.PlayerSetting;
import com.lawkeys.hcfcore.settings.SettingsMenu;
import com.lawkeys.hcfcore.settings.SettingsModule;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Objects;

/** The menu's clicks, the cobblestone switch, and choices put back at join. */
public final class SettingsListener implements Listener {

    private final SettingsModule module;

    public SettingsListener(SettingsModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        module.restore(event.getPlayer());
    }

    /**
     * Cobblestone stays on the ground for a player who switched it off.
     * {@code NORMAL}, so a plugin that decides pickups more firmly - the staff mode's
     * "no pickups" among them - still has the last word.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player
                && module.isCobblestone(event.getItem().getItemStack().getType())
                && !module.isOn(player, PlayerSetting.COBBLESTONE)) {
            event.setCancelled(true);
        }
    }

    /** Recognised by its holder, read with {@code getHolder(false)} - see the ticket menu for why. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof SettingsMenu menu)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getInventory()) {
            return;
        }
        PlayerSetting setting = menu.settingAt(event.getRawSlot());
        if (setting == null) {
            return;
        }
        // A tick later: slots touched by a click can be overwritten by it (the
        // javadoc of InventoryClickEvent).
        Bukkit.getScheduler().runTask(module.getPlugin(), () -> {
            Player viewer = menu.viewer();
            if (viewer.isOnline()) {
                module.toggle(viewer, setting);
                menu.redraw();
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder(false) instanceof SettingsMenu) {
            event.setCancelled(true);
        }
    }
}
