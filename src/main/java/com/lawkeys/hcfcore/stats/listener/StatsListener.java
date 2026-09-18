package com.lawkeys.hcfcore.stats.listener;

import com.lawkeys.hcfcore.stats.StatsModule;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;

/** Counts kills and deaths, and times sessions. */
public final class StatsListener implements Listener {

    private final StatsModule module;

    public StatsListener(StatsModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        module.getManager().beginSession(player.getUniqueId(), player.getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        module.getManager().endSession(event.getPlayer().getUniqueId());
    }

    /**
     * Records the death, and the kill if a player caused it.
     *
     * <p>{@code getKiller()} is the credit Bukkit itself assigns, which already
     * follows a projectile back to whoever fired it - so a bow kill counts for the
     * archer without this having to unwrap anything.
     *
     * <p>A player killing themselves counts the death and not the kill: crediting
     * somebody for their own death would make a killstreak farmable alone, which is
     * the whole point of the streak being worth something.
     *
     * <p>A death another plugin cancels is not one - the player is revived - so it
     * counts nothing, and ends no streak.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        module.getManager().recordDeath(victim.getUniqueId(), victim.getName());

        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim)) {
            module.recordKill(killer);
        }
    }
}
