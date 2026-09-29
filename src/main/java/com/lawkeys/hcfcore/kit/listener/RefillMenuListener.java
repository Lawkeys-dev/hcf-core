package com.lawkeys.hcfcore.kit.listener;

import com.lawkeys.hcfcore.kit.KitModule;
import com.lawkeys.hcfcore.kit.RefillMenu;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;

import java.util.Objects;
import java.util.Set;

/**
 * The rules of a {@link RefillMenu}: take, never give nor throw.
 *
 * <p>An item is taken from the window - picked up, or shift-clicked into the
 * inventory - and the window is restocked a tick later. Everything that would put an
 * item into it (placing, swapping, shift-clicking from the inventory) is refused,
 * and so is throwing one out of the window or from the cursor while it is open:
 * self-service at spawn is not a fountain of items on the ground.
 */
public final class RefillMenuListener implements Listener {

    /** What taking looks like when the window's item goes to the cursor or the inventory. */
    private static final Set<InventoryAction> TAKING = Set.of(InventoryAction.PICKUP_ALL,
            InventoryAction.PICKUP_HALF, InventoryAction.PICKUP_ONE, InventoryAction.PICKUP_SOME,
            InventoryAction.MOVE_TO_OTHER_INVENTORY);

    private final KitModule module;

    public RefillMenuListener(KitModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof RefillMenu menu)) {
            return;
        }
        InventoryAction action = event.getAction();
        boolean throwing = event.getSlotType() == InventoryType.SlotType.OUTSIDE
                || action.name().startsWith("DROP_");
        if (throwing) {
            event.setCancelled(true);
            return;
        }
        boolean inWindow = event.getClickedInventory() != null
                && event.getClickedInventory().equals(event.getView().getTopInventory());
        if (inWindow) {
            if (!TAKING.contains(action) || !event.getCursor().isEmpty()) {
                event.setCancelled(true);
                return;
            }
            Bukkit.getScheduler().runTask(module.getPlugin(), menu::restock);
            return;
        }
        // In the inventory below: anything that would carry an item up into the window.
        if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY || action == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof RefillMenu)) {
            return;
        }
        int top = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < top)) {
            event.setCancelled(true);
        }
    }
}
