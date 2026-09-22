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
 * <p>Both are switches in {@code pvp.yml} ({@code safe-zones.no-damage},
 * {@code keep-fed}), and both follow {@code safe-zones.enabled}: a server that
 * fights on its safe zones keeps their damage too.
 */
public final class SafeZoneListener implements Listener {

    private static final int FULL = 20;

    private final PvpModule module;

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
            feed(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        feed(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        feed(event.getPlayer());
    }

    private void feed(Player player) {
        if (!applies() || !rules().keepFed() || player.getFoodLevel() >= FULL) {
            return;
        }
        if (module.isInSafeZone(player)) {
            player.setFoodLevel(FULL);
            player.setSaturation(FULL);
        }
    }
}
