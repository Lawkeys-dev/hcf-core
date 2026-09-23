package com.lawkeys.hcfcore.pvp.listener;

import com.lawkeys.hcfcore.pvp.PvpModule;
import com.lawkeys.hcfcore.pvp.PvpSettings;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Objects;

/**
 * What a safe zone is worth beyond PvP: no damage of any kind, and no hunger - the
 * project owner's choice, 22/09/2026.
 *
 * <p>Spawn is where players sort themselves out between fights. Drowning in the
 * fountain, burning on a stray fire or starving while trading there is noise, not
 * difficulty, and every server ends up scripting it away.
 *
 * <p>And what a safe zone is worth to somebody running to it: nothing, while they
 * are in combat. A player with a combat tag cannot step into one until it runs out -
 * the classic HCF rule, so a fight is not ended by reaching spawn - and sees the
 * border they may not cross as a wall, drawn by the claim module ahead of them.
 *
 * <p>All of it is switches in {@code pvp.yml} ({@code safe-zones.no-damage},
 * {@code keep-fed}, {@code heal}, {@code block-combat-tagged}, {@code wall}), and all
 * of it follows {@code safe-zones.enabled}: a server that fights on its safe zones
 * keeps their damage too.
 */
public final class SafeZoneListener implements Listener {

    private static final int FULL = 20;

    private final PvpModule module;
    /** A refused step repeats every tick while the player pushes: the reason is said at most this often. */
    private final com.lawkeys.hcfcore.util.RefusalThrottle refusals =
            new com.lawkeys.hcfcore.util.RefusalThrottle(2_000L);

    public SafeZoneListener(PvpModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    private PvpSettings.SafeZoneRules rules() {
        return module.getSettings().safeZones();
    }

    private boolean applies() {
        return module.getSettings().enabled() && rules().enabled();
    }

    /**
     * Every kind of damage, not only a blow: fall, fire, drowning, suffocation, a
     * mob, a cactus. Ally and enemy blows are judged before this, by the combat
     * rules, which already refuse them here.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !applies() || !rules().noDamage()) {
            return;
        }
        // The void and /kill still land: claims run the full height of the world, and
        // a player fallen below a safe zone would otherwise fall for ever.
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.VOID || cause == EntityDamageEvent.DamageCause.KILL) {
            return;
        }
        if (module.isInSafeZone(player)) {
            event.setCancelled(true);
        }
    }

    /** Hunger never drops on a safe zone. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player) || !applies() || !rules().keepFed()) {
            return;
        }
        if (event.getFoodLevel() < player.getFoodLevel() && module.isInSafeZone(player)) {
            event.setCancelled(true);
        }
    }

    /** Walking in fills the bar back up: a player leaves spawn fed, as they arrived. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            feed(event.getPlayer(), event.getTo());
        }
    }

    /** Judged where the player arrives: during the event they still stand where they left. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleported(PlayerTeleportEvent event) {
        feed(event.getPlayer(), event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        feed(event.getPlayer(), event.getPlayer().getLocation());
    }

    /**
     * Refuses a player in combat the step into a safe zone. Checked block by block,
     * like a locked claim: a safe zone's border runs inside a chunk.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnter(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        if (refuseEntry(event.getPlayer(), event.getTo())) {
            event.setCancelled(true);
        }
    }

    /** A pearl, a chorus fruit or any other jump into a safe zone, while in combat. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onJump(PlayerTeleportEvent event) {
        if (refuseEntry(event.getPlayer(), event.getTo())) {
            event.setCancelled(true);
        }
    }

    /**
     * @return whether this player may not be there: in combat, and that land is a
     *         safe zone. Told at most every few seconds, as the refusal repeats for
     *         as long as they push against it
     */
    private boolean refuseEntry(Player player, org.bukkit.Location to) {
        if (!applies() || !rules().blockCombatTagged() || to == null || to.getWorld() == null) {
            return false;
        }
        if (module.getCombatTags() == null || !module.getCombatTags().isTagged(player.getUniqueId())) {
            return false;
        }
        if (!module.isSafeZoneAt(to)) {
            return false;
        }
        if (refusals.tryTell(player.getUniqueId(), System.currentTimeMillis())) {
            module.getLang().send(player, com.lawkeys.hcfcore.pvp.PvpMessages.SAFE_ZONE_COMBAT,
                    "seconds", String.valueOf(module.getCombatTags().getRemainingSeconds(player.getUniqueId())));
        }
        return true;
    }

    /** Health and hunger back to full on safe-zone land. */
    private void feed(Player player, org.bukkit.Location at) {
        if (!applies() || !module.isSafeZoneAt(at)) {
            return;
        }
        if (rules().keepFed() && player.getFoodLevel() < FULL) {
            player.setFoodLevel(FULL);
            player.setSaturation(FULL);
        }
        if (rules().heal()) {
            double max = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) == null
                    ? 20.0 : player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
            if (player.getHealth() < max) {
                player.setHealth(max);
            }
        }
    }
}
