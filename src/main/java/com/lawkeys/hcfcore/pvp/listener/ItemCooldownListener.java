package com.lawkeys.hcfcore.pvp.listener;

import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.pvp.PvpMessages;
import com.lawkeys.hcfcore.pvp.PvpModule;
import com.lawkeys.hcfcore.pvp.PvpSettings;
import com.lawkeys.hcfcore.util.Durations;
import com.lawkeys.hcfcore.util.RefusalThrottle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.Optional;

/**
 * Item cooldowns ({@code pvp.yml}, {@code item-cooldowns}): an item eaten - a golden
 * apple, a chorus fruit - or a totem that saves its holder starts its cooldown; used
 * again meanwhile, it is refused. The refusal is decided at {@code HIGHEST}, after
 * whatever else refuses the item; the cooldown starts at {@code MONITOR}, once the
 * use has really happened.
 */
public final class ItemCooldownListener implements Listener {

    private static final long MESSAGE_EVERY_MILLIS = 1_000L;

    private final PvpModule module;
    private final RefusalThrottle refusals = new RefusalThrottle(MESSAGE_EVERY_MILLIS);

    public ItemCooldownListener(PvpModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    private Optional<PvpSettings.ItemCooldown> cooldownOf(ItemStack item) {
        PvpSettings.ItemCooldownRules rules = module.getSettings().itemCooldowns();
        if (!rules.enabled() || item == null || item.isEmpty() || module.getPartnerItems().test(item)) {
            return Optional.empty();
        }
        return rules.of(item.getType().name());
    }

    /** @return whether it is refused - the player having been told */
    private boolean refuse(Player player, PvpSettings.ItemCooldown item) {
        long left = module.itemCooldownLeft(player, item);
        if (left <= 0) {
            return false;
        }
        if (refusals.tryTell(player.getUniqueId(), System.currentTimeMillis())) {
            module.getLang().send(player, PvpMessages.ITEM_COOLDOWN, "item", LangManager.colorize(item.name()),
                    "time", Durations.formatWithSeconds(left));
        }
        return true;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onEat(PlayerItemConsumeEvent event) {
        cooldownOf(event.getItem()).filter(item -> refuse(event.getPlayer(), item))
                .ifPresent(item -> event.setCancelled(true));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEaten(PlayerItemConsumeEvent event) {
        cooldownOf(event.getItem()).ifPresent(item -> module.startItemCooldown(event.getPlayer(), item));
    }

    /** A totem on cooldown saves nobody: the death goes ahead. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onTotem(EntityResurrectEvent event) {
        if (event.getEntity() instanceof Player player) {
            totem(player, event).filter(item -> refuse(player, item)).ifPresent(item -> event.setCancelled(true));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onSaved(EntityResurrectEvent event) {
        if (event.getEntity() instanceof Player player) {
            totem(player, event).ifPresent(item -> module.startItemCooldown(player, item));
        }
    }

    private Optional<PvpSettings.ItemCooldown> totem(Player player, EntityResurrectEvent event) {
        return event.getHand() == null ? Optional.empty()
                : cooldownOf(player.getInventory().getItem(event.getHand()));
    }

    /** The greyed-out items come back after a login: the game forgets them, the player's data does not. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        for (PvpSettings.ItemCooldown item : module.getSettings().itemCooldowns().items()) {
            module.showItemCooldown(event.getPlayer(), item);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        for (PvpSettings.ItemCooldown item : module.getSettings().itemCooldowns().items()) {
            if (item.clearOnDeath()) {
                module.clearItemCooldown(event.getPlayer(), item);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        refusals.forget(event.getPlayer().getUniqueId());
    }
}
