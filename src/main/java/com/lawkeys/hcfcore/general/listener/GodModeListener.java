package com.lawkeys.hcfcore.general.listener;

import com.lawkeys.hcfcore.general.GeneralModule;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;

/**
 * {@code /god}: a player in god mode takes no damage and gets no hungrier. Refused
 * first ({@code LOWEST}), so no other module draws a consequence from a hit that
 * does not happen - no combat tag for a blow on a god.
 */
public final class GodModeListener implements Listener {

    private final GeneralModule module;

    public GodModeListener(GeneralModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && module.isGod(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && module.isGod(player.getUniqueId())
                && event.getFoodLevel() < player.getFoodLevel()) {
            event.setCancelled(true);
        }
    }

    /** God mode lasts the session: a logout ends it. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        module.setGod(event.getPlayer().getUniqueId(), false);
    }
}
