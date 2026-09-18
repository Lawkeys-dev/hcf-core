package com.lawkeys.hcfcore.crowbar.listener;

import com.lawkeys.hcfcore.crowbar.CrowbarModule;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Objects;

/** A right-click on an End portal frame with a crowbar in the main hand. */
public final class CrowbarListener implements Listener {

    private final CrowbarModule module;

    public CrowbarListener(CrowbarModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    /**
     * The interaction is cancelled whatever happens next, so a crowbar never also
     * does what its base item would - a hoe tilling, or an eye going into the frame.
     * Only the main hand: the event fires once per hand.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND
                || block == null || block.getType() != Material.END_PORTAL_FRAME
                || !module.isCrowbar(event.getPlayer().getInventory().getItemInMainHand())) {
            return;
        }
        event.setCancelled(true);
        module.use(event.getPlayer(), block);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        module.forget(event.getPlayer().getUniqueId());
    }
}
