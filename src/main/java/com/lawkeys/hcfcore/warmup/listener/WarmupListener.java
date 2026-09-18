package com.lawkeys.hcfcore.warmup.listener;

import com.lawkeys.hcfcore.warmup.WarmupModule;
import com.lawkeys.hcfcore.warmup.Warmups;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Objects;

/**
 * Cuts a countdown short when the player takes damage or moves.
 *
 * <p>That is the whole point of the countdown: none of the commands behind one may
 * be a way out of a fight, and a warmup nobody could interrupt would be exactly
 * that.
 */
public final class WarmupListener implements Listener {

    private final WarmupModule module;

    public WarmupListener(WarmupModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && module.isWarmingUp(player.getUniqueId())) {
            module.cancel(player);
        }
    }

    /**
     * Moving cancels it - but only leaving the block, not turning on the spot.
     * Cancelling on any move event at all would make a countdown impossible to
     * finish, since looking around fires it.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Warmups.Warmup warmup = module.of(player.getUniqueId()).orElse(null);
        if (warmup == null) {
            return;
        }
        Location to = event.getTo();
        if (warmup.hasMoved(to.getWorld().getName(), to.getBlockX(), to.getBlockY(), to.getBlockZ())) {
            module.cancel(player);
        }
    }

    /**
     * A teleport is a move too: an ender pearl, a chorus fruit, a portal.
     *
     * <p>{@link PlayerTeleportEvent} and {@link PlayerPortalEvent} each declare their
     * own handler list (26.2 sources), so {@link #onMove} never saw them - a pearl
     * thrown to flee a fight left a {@code /spawn} or {@code /team hq} countdown
     * running (noted in the listener review, 14/09/2026). A countdown's own teleport
     * happens once it has finished and been dropped, so it cancels nothing.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        onMove(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        onMove(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        module.forget(event.getPlayer().getUniqueId());
    }
}
