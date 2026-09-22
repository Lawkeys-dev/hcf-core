package com.lawkeys.hcfcore.claim.listener;

import com.lawkeys.hcfcore.claim.ClaimMessages;
import com.lawkeys.hcfcore.claim.ClaimModule;
import com.lawkeys.hcfcore.team.Team;
import com.lawkeys.hcfcore.util.RefusalThrottle;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps non-members out of a claim locked during SOTW (see {@code /team lockclaim}).
 *
 * <p>Walking in is refused at the chunk border; so are the two ways of jumping
 * over it a camper would use, an ender pearl and a chorus fruit
 * ({@code TeleportCause.ENDER_PEARL} and {@code CONSUMABLE_EFFECT}, which the 26.2
 * sources give chorus fruit) - and a portal, at the portal and again on arrival.
 * Teleports by commands and plugins are left alone: a
 * member's {@code /team hq} lands inside by right, and a staff teleport is staff's
 * business.
 */
public final class ClaimLockListener implements Listener {

    /** A refused step repeats every tick while the player pushes: the reason is said at most this often. */
    private static final long MESSAGE_EVERY_MILLIS = 2_000L;

    private static final Set<PlayerTeleportEvent.TeleportCause> JUMPS =
            Set.of(PlayerTeleportEvent.TeleportCause.ENDER_PEARL, PlayerTeleportEvent.TeleportCause.CONSUMABLE_EFFECT);

    private final ClaimModule module;
    private final RefusalThrottle refusalMessages = new RefusalThrottle(MESSAGE_EVERY_MILLIS);
    /** Players between a portal and their arrival on the other side. */
    private final Set<UUID> throughPortal = ConcurrentHashMap.newKeySet();

    public ClaimLockListener(ClaimModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    /**
     * Every block stepped into is checked, not every chunk: claims have been
     * block-precise since 22/09/2026, so a claim's border runs inside a chunk and a
     * step across it within one chunk went through unchecked - walking into a locked
     * claim from a metre away (the project owner's report, 22/09/2026). The lookup
     * itself is a hash of the chunk, so checking each block costs no more than
     * checking each chunk did.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()
                && Objects.equals(from.getWorld(), to.getWorld())) {
            return;
        }
        refused(event.getPlayer(), to).ifPresent(team -> {
            event.setCancelled(true);
            tell(event.getPlayer(), team);
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!JUMPS.contains(event.getCause())) {
            return;
        }
        refused(event.getPlayer(), event.getTo()).ifPresent(team -> {
            event.setCancelled(true);
            tell(event.getPlayer(), team);
        });
    }

    /**
     * A portal whose exit is in a locked claim.
     *
     * <p>{@code PlayerPortalEvent} declares its own handler list (26.2 sources), so
     * {@link #onTeleport} never saw it (noted in the listener review, 14/09/2026).
     * Refused here when the destination is known to be inside - but for a nether
     * portal that destination is only "the starting point for search" (its javadoc),
     * the exit being settled afterwards within a radius; so arrival is checked again
     * in {@link #onChangedWorld}.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        refused(event.getPlayer(), event.getTo()).ifPresent(team -> {
            event.setCancelled(true);
            tell(event.getPlayer(), team);
        });
    }

    /** Remembers a portal actually taken - nothing later refused it - for {@link #onChangedWorld}. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPortalTaken(PlayerPortalEvent event) {
        throughPortal.add(event.getPlayer().getUniqueId());
    }

    /**
     * Arriving through a portal in a locked claim anyway: moved out to the nearest
     * free land, as locking moves out whoever is already inside. Only after a portal:
     * a command or plugin teleport is left alone, as the class says.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (!throughPortal.remove(player.getUniqueId())) {
            return;
        }
        Location at = player.getLocation();
        refused(player, at).ifPresent(team -> module.nearestFreeLand(ClaimModule.toChunk(at),
                        module.getTeams().getManager().getTeamOf(player.getUniqueId()).map(Team::getId).orElse(null))
                .ifPresent(destination -> module.moveTo(player, destination, () -> module.getLang()
                        .send(player, ClaimMessages.LOCK_EXPELLED, "team", team.getName()))));
    }

    /**
     * Reaching into a locked claim from outside: a door, a chest, a button, a lever.
     * The lock closes the claim to everybody but its members, and a claim whose doors
     * open from the border is not closed.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) {
            return;
        }
        refused(event.getPlayer(), event.getClickedBlock().getLocation()).ifPresent(team -> {
            event.setCancelled(true);
            tell(event.getPlayer(), team);
        });
    }

    /** A boat, a horse or a minecart carrying a player into a locked claim. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleMove(org.bukkit.event.vehicle.VehicleMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        for (org.bukkit.entity.Entity passenger : event.getVehicle().getPassengers()) {
            if (!(passenger instanceof Player player)) {
                continue;
            }
            refused(player, to).ifPresent(team -> {
                event.getVehicle().setVelocity(new org.bukkit.util.Vector());
                event.getVehicle().teleport(from);
                tell(player, team);
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        refusalMessages.forget(event.getPlayer().getUniqueId());
        throughPortal.remove(event.getPlayer().getUniqueId());
    }

    private Optional<Team> refused(Player player, Location to) {
        // The permission alone, unlike building (ClaimModule#bypassesProtection):
        // walking into a locked claim is moderation, not editing somebody's base.
        if (module.getManager() == null || player.hasPermission(ClaimProtectionListener.BYPASS_PERMISSION)) {
            return Optional.empty();
        }
        return module.getManager().lockedAgainst(to.getWorld().getName(), to.getBlockX(), to.getBlockZ(),
                player.getUniqueId());
    }

    private void tell(Player player, Team team) {
        if (refusalMessages.tryTell(player.getUniqueId(), System.currentTimeMillis())) {
            module.getLang().send(player, ClaimMessages.LOCK_ENTRY_DENIED, "team", team.getName());
        }
    }
}
