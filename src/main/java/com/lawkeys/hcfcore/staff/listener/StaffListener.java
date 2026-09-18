package com.lawkeys.hcfcore.staff.listener;

import com.lawkeys.hcfcore.chat.ChatModule;
import com.lawkeys.hcfcore.staff.StaffMessages;
import com.lawkeys.hcfcore.staff.StaffModule;
import com.lawkeys.hcfcore.staff.ToolbarItem;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.UUID;

/**
 * The server-facing half of the staff module: the toolbar, the mode's extras, and
 * the staff channel.
 *
 * <p><strong>On which events are listened to.</strong> Two of the classes below are
 * parents chosen on purpose. {@code EntityDamageByEntityEvent} and
 * {@code EntityTargetLivingEntityEvent} declare no {@code HandlerList} of their
 * own, so a listener on the parent receives them - checked in the Paper 26.2
 * sources, because the reverse case is the trap Kill the King hit, where a subclass
 * <em>did</em> own a list and its events were silently never delivered.
 */
public final class StaffListener implements Listener {

    private final StaffModule module;

    public StaffListener(StaffModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    // ------------------------------------------------------------------
    // Session boundaries
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Anything held from a session that ended badly goes back now.
        module.restoreAfterRestart(player);
        // And they must not see staff who are currently hidden.
        module.applyVanishTo(player);
    }

    /**
     * Takes a leaving staff member out of the mode.
     *
     * <p>{@code HIGHEST} rather than {@code MONITOR}: this changes the player's
     * inventory, and a monitor handler is supposed to observe rather than act. It
     * has to happen before they are gone, or the toolbar would be saved as their
     * real inventory and the stash would wait for their next login to undo it.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        if (module.getManager().isInStaffMode(playerId)) {
            // Quietly: they are leaving and will not read the confirmation.
            module.leaveStaffMode(player, false);
        }
        if (module.getManager().isVanished(playerId)) {
            // So they are not still hidden from everybody when they come back.
            module.showToEveryone(player);
        }
        module.getManager().forgetSession(playerId);
    }

    // ------------------------------------------------------------------
    // The toolbar
    // ------------------------------------------------------------------

    /** A toolbar item used on air or a block: runs its command with no target. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!module.getManager().isInStaffMode(player.getUniqueId())) {
            return;
        }
        ToolbarItem binding = module.bindingOf(event.getItem());
        if (binding == null) {
            return;
        }
        // Always cancelled, whether or not the binding runs: a staff tool must never
        // also place a block or open a chest.
        event.setCancelled(true);
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) {
            // Offhand fires a second event for the same click; one run is enough.
            return;
        }
        run(player, binding, null);
    }

    /** A toolbar item used on a player: runs its command against them. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (!module.getManager().isInStaffMode(player.getUniqueId())) {
            return;
        }
        ItemStack used = player.getInventory().getItem(event.getHand());
        ToolbarItem binding = module.bindingOf(used);
        if (binding == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) {
            return;
        }
        Entity clicked = event.getRightClicked();
        run(player, binding, clicked instanceof Player target ? target.getName() : null);
    }

    private void run(Player player, ToolbarItem binding, String targetName) {
        if (!binding.isUsable(targetName)) {
            module.getLang().send(player, StaffMessages.MODE_ITEM_NEEDS_TARGET);
            return;
        }
        // As the staff member, not as the console: the command they run must be the
        // one their own permissions allow, and must name them as the actor.
        player.performCommand(binding.commandFor(player.getName(), targetName));
    }

    /** Toolbar items cannot be thrown away: they are not the staff member's property. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (module.isToolbarItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    /** Nor moved into a container, which is how one would leave staff mode behind. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (module.isToolbarItem(event.getCurrentItem()) || module.isToolbarItem(event.getCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (module.isToolbarItem(event.getMainHandItem()) || module.isToolbarItem(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------
    // The mode's extras
    // ------------------------------------------------------------------

    /**
     * Staff mode takes no damage and deals none.
     *
     * <p>One handler for both halves, because {@code EntityDamageByEntityEvent}
     * shares this event's handler list - so the damager is reachable from here
     * without a second registration.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (!module.getSettings().staffMode().invulnerable()) {
            return;
        }
        if (event.getEntity() instanceof Player victim
                && module.getManager().isInStaffMode(victim.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (event instanceof EntityDamageByEntityEvent byEntity
                && byEntity.getDamager() instanceof Player attacker
                && module.getManager().isInStaffMode(attacker.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** Mobs do not notice somebody who is only watching. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTarget(EntityTargetEvent event) {
        if (!module.getSettings().staffMode().invulnerable()) {
            return;
        }
        if (event.getTarget() instanceof Player player
                && module.getManager().isInStaffMode(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /**
     * Staff mode picks nothing up.
     *
     * <p>Not a nicety: a staff member standing over the drops of the fight they are
     * watching would otherwise hoover up the loot, and it would land in a toolbar
     * inventory that is emptied when they leave the mode.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPickup(EntityPickupItemEvent event) {
        if (!module.getSettings().staffMode().invulnerable()) {
            return;
        }
        if (event.getEntity() instanceof Player player
                && module.getManager().isInStaffMode(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------
    // Staff chat
    // ------------------------------------------------------------------

    /**
     * Routes a staff member's chat to the staff channel while they have it on.
     *
     * <p>{@code LOW}, ahead of the chat module's router at {@code NORMAL}, which
     * cancels a line bound for team chat: at {@code HIGHEST} this handler never saw
     * such a line, and a staff member who had turned the staff channel on with their
     * team channel still on sent what they meant for staff to their team (found in
     * review, 14/09/2026). Of two channels switched on, the staff one wins: a staff
     * line in team chat is a leak, the other way round is not.
     *
     * <p>This event is normally asynchronous. The message is therefore read here and
     * the sending is handed to the main thread, rather than walking the online
     * player list off it.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!module.getSettings().enabled() || !module.getSettings().staffChat().enabled()) {
            return;
        }
        if (!module.getManager().isStaffChatOn(player.getUniqueId())
                || !player.hasPermission(StaffModule.STAFF_PERMISSION)) {
            return;
        }
        event.setCancelled(true);
        String message = ChatModule.typedText(player,
                PlainTextComponentSerializer.plainText().serialize(event.message()));
        String line = module.getSettings().staffChat().format()
                .replace("%player%", player.getName())
                .replace("%message%", message);
        Bukkit.getScheduler().runTask(module.getPlugin(),
                () -> module.sendToStaffChannel(com.lawkeys.hcfcore.lang.LangManager.colorize(line)));
    }
}
