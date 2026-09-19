package com.lawkeys.hcfcore.pvp.listener;

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import com.lawkeys.hcfcore.pvp.PvpMessages;
import com.lawkeys.hcfcore.pvp.PvpModule;
import com.lawkeys.hcfcore.pvp.PvpSettings;
import com.lawkeys.hcfcore.util.Durations;
import com.lawkeys.hcfcore.util.RefusalThrottle;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;

/**
 * The HCF ender pearl cooldown ({@code pvp.yml}, {@code ender-pearl-cooldown}): a
 * pearl thrown starts it, and a pearl thrown while it runs is refused and stays in
 * hand. At {@code HIGHEST}, after whatever else refuses a pearl - a Citadel, a
 * freeze - so a pearl refused elsewhere starts nothing.
 */
public final class PearlListener implements Listener {

    /** The cooldown's key in the module's {@code Cooldowns}. */
    public static final String KEY = "pearl";
    private static final long MESSAGE_EVERY_MILLIS = 1_000L;

    private final PvpModule module;
    private final RefusalThrottle refusals = new RefusalThrottle(MESSAGE_EVERY_MILLIS);

    public PearlListener(PvpModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPearl(PlayerLaunchProjectileEvent event) {
        PvpSettings.EnderPearlRules rules = module.getSettings().enderPearl();
        if (!rules.enabled() || rules.seconds() <= 0 || !(event.getProjectile() instanceof EnderPearl)
                || module.getPartnerItems().test(event.getItemStack())) {
            return;
        }
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        long left = module.getPearlCooldowns().remaining(player.getUniqueId(), KEY, now);
        if (left > 0) {
            event.setShouldConsume(false);
            event.setCancelled(true);
            if (refusals.tryTell(player.getUniqueId(), now)) {
                module.getLang().send(player, PvpMessages.PEARL_COOLDOWN, "time", Durations.formatWithSeconds(left));
            }
            return;
        }
        module.getPearlCooldowns().start(player.getUniqueId(), KEY, rules.seconds(), now);
        if (rules.showOnItem()) {
            // After the game's own one-second cooldown, and the classic combat's clearing of it.
            long ticks = rules.seconds() * 20L;
            Bukkit.getScheduler().runTaskLater(module.getPlugin(), () -> {
                if (player.isOnline()) {
                    player.setCooldown(Material.ENDER_PEARL, (int) Math.max(0L, ticks - 2L));
                }
            }, 2L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (module.getSettings().enderPearl().clearOnDeath()) {
            Player player = event.getPlayer();
            module.resetPearl(player.getUniqueId());
            player.setCooldown(Material.ENDER_PEARL, 0);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        refusals.forget(event.getPlayer().getUniqueId());
    }
}
