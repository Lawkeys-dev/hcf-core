package com.lawkeys.hcfcore.staff.listener;

import com.lawkeys.hcfcore.staff.StaffModule;
import com.lawkeys.hcfcore.staff.command.TicketMenu;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import java.util.Objects;

/**
 * Turns clicks in the ticket menu into actions, and keeps its items where they are.
 *
 * <p>The menu is recognised by its holder, read with {@code getHolder(false)}: this
 * handler sees every click in every window on the server, and the plain
 * {@code getHolder()} builds a snapshot of the block behind a chest or a furnace
 * each time it is asked.
 */
public final class TicketMenuListener implements Listener {

    private final StaffModule module;

    public TicketMenuListener(StaffModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(menuOf(event.getInventory()) instanceof TicketMenu menu)) {
            return;
        }
        // Every click, in either half of the window: a shift-click from the
        // player's own inventory would otherwise put an item into the menu.
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player staff)
                || event.getClickedInventory() != event.getInventory()
                || !(event.isLeftClick() || event.isRightClick())) {
            return;
        }
        long ticketId = menu.ticketAt(event.getRawSlot());
        if (ticketId < 0) {
            return;
        }
        boolean leftClick = event.isLeftClick();
        Bukkit.getScheduler().runTask(module.getPlugin(), () -> {
            // Checked again a tick later: the menu can outlive the permission.
            if (staff.isOnline() && staff.hasPermission(StaffModule.STAFF_PERMISSION)) {
                menu.act(staff, ticketId, leftClick);
            }
        });
    }

    /** A drag is its own event, with its own handler list, and must not move items either. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (menuOf(event.getInventory()) instanceof TicketMenu) {
            event.setCancelled(true);
        }
    }

    private static Object menuOf(Inventory inventory) {
        return inventory.getHolder(false);
    }
}
