package com.lawkeys.hcfcore.kit.listener;

import com.lawkeys.hcfcore.kit.KitLayout;
import com.lawkeys.hcfcore.kit.KitMessages;
import com.lawkeys.hcfcore.kit.KitModule;
import com.lawkeys.hcfcore.kit.command.KitLayoutMenu;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Keeps the kit layout editor's copies inside it, and saves the layout on close.
 *
 * <p>Inside the editor only three moves are allowed: pick a whole stack up, put it
 * down, swap it with the one on the cursor. Everything that could split, merge, copy
 * or carry a stack out - half stacks, drags, shift-clicks, number keys, the off-hand
 * key, double-click collect, drops, creative's middle click, any click in the
 * player's own inventory - is cancelled. {@code InventoryDragEvent} declares its own
 * handler list (26.2 sources), so it has its own handler; creative clicks do not, and
 * reach the click handler.
 *
 * <p>And, as a net under all of that, a marked copy found anywhere else is destroyed:
 * on the cursor as the window closes, in the inventory the tick after, at login, and
 * when one would spawn as a dropped item. A net because the order in which the server
 * hands the cursor back on close is not documented; what is certain is that nothing
 * marked survives it.
 */
public final class KitLayoutListener implements Listener {

    /** The only moves allowed inside the editor. */
    private static final Set<InventoryAction> ALLOWED =
            EnumSet.of(InventoryAction.PICKUP_ALL, InventoryAction.PLACE_ALL, InventoryAction.SWAP_WITH_CURSOR);

    private final KitModule module;

    public KitLayoutListener(KitModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof KitLayoutMenu menu)) {
            // Elsewhere: a marked copy that got out anyhow is not usable.
            if (module.layoutSlotOf(event.getCurrentItem()) != null || module.layoutSlotOf(event.getCursor()) != null) {
                event.setCancelled(true);
                if (event.getWhoClicked() instanceof Player player) {
                    Bukkit.getScheduler().runTask(module.getPlugin(), () -> module.purgeLayoutCopies(player));
                }
            }
            return;
        }
        boolean inside = event.getClickedInventory() != null
                && event.getClickedInventory().equals(event.getView().getTopInventory());
        if (!inside || event.getSlot() > KitLayout.EDITOR_OFF_HAND || !ALLOWED.contains(event.getAction())
                || (!event.getCursor().isEmpty() && module.layoutSlotOf(event.getCursor()) == null)) {
            event.setCancelled(true);
        }
        if (inside && event.getSlot() == KitLayoutMenu.RESET_SLOT && event.getWhoClicked() instanceof Player player) {
            // A tick later: the cursor cannot be changed safely inside the click.
            Bukkit.getScheduler().runTask(module.getPlugin(), () -> {
                if (module.layoutSlotOf(player.getItemOnCursor()) != null) {
                    player.setItemOnCursor(null);
                }
                menu.arrange(KitLayout.NONE);
            });
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof KitLayoutMenu) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof KitLayoutMenu menu)
                || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        KitLayout layout = menu.read(player.getItemOnCursor());
        if (module.layoutSlotOf(player.getItemOnCursor()) != null) {
            player.setItemOnCursor(null);
        }
        KitLayout before = module.getManager().layout(menu.playerId(), menu.kit().id());
        if (!layout.equals(before)) {
            module.getManager().setLayout(menu.playerId(), menu.kit().id(), layout);
            module.flushSoon();
            module.getLang().send(player, layout.isEmpty() ? KitMessages.LAYOUT_RESET : KitMessages.LAYOUT_SAVED,
                    "kit", menu.kit().displayName());
        }
        Bukkit.getScheduler().runTask(module.getPlugin(), () -> module.purgeLayoutCopies(player));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        module.purgeLayoutCopies(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSpawn(ItemSpawnEvent event) {
        ItemStack item = event.getEntity().getItemStack();
        if (module.layoutSlotOf(item) != null) {
            event.setCancelled(true);
        }
    }
}
