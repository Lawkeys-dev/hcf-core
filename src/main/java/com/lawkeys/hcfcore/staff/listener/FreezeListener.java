package com.lawkeys.hcfcore.staff.listener;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.lawkeys.hcfcore.staff.StaffMessages;
import com.lawkeys.hcfcore.staff.StaffModule;
import com.lawkeys.hcfcore.util.RefusalThrottle;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.connection.PlayerConnection;
import io.papermc.paper.connection.PlayerLoginConnection;
import io.papermc.paper.event.connection.PlayerConnectionValidateLoginEvent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Objects;
import java.util.UUID;

/**
 * Holds frozen players in place, and keeps out anybody who ran from a check.
 *
 * <p><strong>What a freeze blocks, and what it deliberately does not.</strong> A
 * frozen player cannot move, build, break, interact, drop, fight, be fought, or run
 * a command that is not on the allow-list. They <em>can</em> look around and talk,
 * because a check is a conversation: a player who cannot answer can only sit there
 * or disconnect, and disconnecting is the single thing the hold exists to
 * discourage.
 *
 * <p>Damage is blocked in both directions so a held player cannot be killed while
 * they wait - a hold that got somebody killed by a passing mob would be a
 * punishment nobody chose.
 */
public final class FreezeListener implements Listener {

    /** How often a frozen player is told that an attempt was refused. */
    private static final long MESSAGE_EVERY_MILLIS = 2_000L;

    private final StaffModule module;
    private final RefusalThrottle refusalMessages = new RefusalThrottle(MESSAGE_EVERY_MILLIS);

    public FreezeListener(StaffModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    private boolean isFrozen(Player player) {
        return module.getSettings().freeze().enabled()
                && module.getFreezes().isFrozen(player.getUniqueId());
    }

    /**
     * Keeps them where they are.
     *
     * <p>Only a change of block is refused, not a change of angle: cancelling every
     * move event would lock their camera too, which reads as a crash rather than as
     * a hold.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!isFrozen(event.getPlayer())) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        event.setCancelled(true);
    }

    /** And nothing else may move them either - a pearl thrown before the freeze, a plugin. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (isFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /**
     * Nor a portal they were standing in when frozen. {@code PlayerPortalEvent}
     * declares its own handler list (26.2 sources), so the handler above never sees
     * it - the trap Kill the King hit.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (isFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!isFrozen(player)) {
            return;
        }
        // The leading slash is part of the message here.
        String typed = event.getMessage().startsWith("/")
                ? event.getMessage().substring(1)
                : event.getMessage();
        if (module.getSettings().freeze().allows(typed)) {
            return;
        }
        event.setCancelled(true);
        module.getLang().send(player, StaffMessages.FREEZE_BLOCKED);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        refuse(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        refuse(event.getPlayer(), event);
    }

    /**
     * Refuses a frozen player's clicks <em>before</em> anybody acts on them, block
     * and item alike.
     *
     * <p>At {@code LOWEST} rather than {@code HIGHEST} like the handlers around it,
     * because a click is not only refused but acted on: kit refill signs and partner
     * items do something on the click itself, and a refusal that comes after them
     * undoes nothing. And without {@code ignoreCancelled}, because a click on air
     * arrives already cancelled - {@code isCancelled()} is only
     * {@code useInteractedBlock() == DENY}, and there is no block - while the item
     * in hand would still be used (Paper 26.2 sources, {@code PlayerInteractEvent}).
     * Until 13/09/2026 a frozen player could still take a kit from a sign, fire an
     * ability and throw an ender pearl.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!isFrozen(player)) {
            return;
        }
        // Both halves: the block, and the item in hand.
        event.setCancelled(true);
        // Said for a real attempt only: not for every swing at the air, a pressure
        // plate or the off hand's copy of the same click.
        if (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.PHYSICAL
                && event.getHand() != EquipmentSlot.OFF_HAND) {
            tell(player);
        }
    }

    /**
     * A right click on an entity: mounting a horse or a boat, trading with a villager,
     * opening a chest minecart, a lead, shears, a saddle.
     *
     * <p>The block-and-item refusal above never sees it - an entity click is its own
     * event - so a held player could ride off or trade (noted in the listener review,
     * 14/09/2026). {@code LOWEST} for the same reason as {@link #onInteract}: refused
     * before anything acts on it. The armour stand's own event is fired from inside
     * this one's interaction, so refusing this refuses it too (26.2 sources).
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (!isFrozen(player)) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.OFF_HAND) {
            tell(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        refuse(event.getPlayer(), event);
    }

    private void refuse(Player player, org.bukkit.event.Cancellable event) {
        if (isFrozen(player)) {
            event.setCancelled(true);
            tell(player);
        }
    }

    /**
     * Rationed: a held button repeats its click five times a second, and a held drop
     * key its drop, each refused and each once said. A command is not rationed - it
     * was typed on purpose, and gets its answer every time.
     */
    private void tell(Player player) {
        if (refusalMessages.tryTell(player.getUniqueId(), System.currentTimeMillis())) {
            module.getLang().send(player, StaffMessages.FREEZE_BLOCKED);
        }
    }

    /**
     * A held player neither takes damage nor deals it.
     *
     * <p>One handler for both, because {@code EntityDamageByEntityEvent} shares this
     * event's handler list - verified in the 26.2 sources.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player victim && isFrozen(victim)) {
            event.setCancelled(true);
            return;
        }
        if (event instanceof EntityDamageByEntityEvent byEntity
                && byEntity.getDamager() instanceof Player attacker && isFrozen(attacker)) {
            event.setCancelled(true);
        }
    }

    /**
     * Running from a check earns a ban that only staff can lift.
     *
     * <p>{@code MONITOR}: the disconnect has happened and cannot be cancelled, so
     * this only records the consequence.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        refusalMessages.forget(playerId);
        if (!module.getFreezes().unfreeze(playerId)) {
            return;
        }
        if (!module.getSettings().freeze().banOnLogout()) {
            return;
        }
        String reason = module.getLang().get(StaffMessages.BAN_REASON_FROZEN_LOGOUT);
        if (module.getBans().ban(playerId, reason, player.getName(), System.currentTimeMillis())) {
            module.flushBansSoon();
            module.announceToStaff(StaffMessages.FREEZE_LOGOUT_ANNOUNCE, player);
        }
    }

    /**
     * Refuses the connection of somebody who is still banned.
     *
     * <p>Same event and the same reasoning as the deathban check: no {@code Player}
     * exists yet, so no permission is asked - and none is wanted here, since a
     * moderation ban that staff could walk through would not be one. Both phases are
     * checked, and a refusal already in place is left alone.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onValidateLogin(PlayerConnectionValidateLoginEvent event) {
        if (!event.isAllowed() || !module.getSettings().enabled()) {
            return;
        }
        UUID playerId = uuidOf(event.getConnection());
        if (playerId == null || !module.getBans().isBanned(playerId)) {
            return;
        }
        event.kickMessage(com.lawkeys.hcfcore.util.LegacyText.SERIALIZER
                .deserialize(module.getLang().get(StaffMessages.BAN_LOGIN_DENIED)));
    }

    /** @see com.lawkeys.hcfcore.pvp.listener.DeathbanListener for the same lookup and why */
    private static UUID uuidOf(PlayerConnection connection) {
        PlayerProfile profile = switch (connection) {
            case PlayerLoginConnection login -> login.getAuthenticatedProfile();
            case PlayerConfigurationConnection configuring -> configuring.getProfile();
            default -> null;
        };
        return profile == null ? null : profile.getId();
    }
}
