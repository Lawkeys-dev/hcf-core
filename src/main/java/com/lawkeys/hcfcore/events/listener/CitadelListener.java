package com.lawkeys.hcfcore.events.listener;

import com.lawkeys.hcfcore.events.CitadelDefinition;
import com.lawkeys.hcfcore.events.CitadelRules;
import com.lawkeys.hcfcore.events.EventMessages;
import com.lawkeys.hcfcore.events.EventModule;
import com.lawkeys.hcfcore.util.RefusalThrottle;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.Optional;

/**
 * What a Citadel's claim refuses, at all times (events.yml, {@code citadels:}): ender
 * pearls, partner items, chorus fruit, elytra and Riptide - the ways out of a fight,
 * and the items that win one without the team. Class abilities are not partner items
 * and stay allowed, so the fight there is between teams and their classes.
 *
 * <p>Items are refused, never the block clicked (CONTRIBUTING.md section 7): a door
 * in the Citadel still opens for whoever holds a pearl. The refusals run at
 * {@code LOWEST}, before the partner items and the classes read the click at
 * {@code LOW}; both skip an item already refused.
 */
public final class CitadelListener implements Listener {

    private static final long MESSAGE_EVERY_MILLIS = 2_000L;

    private final EventModule module;
    private final RefusalThrottle refusals = new RefusalThrottle(MESSAGE_EVERY_MILLIS);

    public CitadelListener(EventModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    /**
     * A pearl thrown, a partner item used or a Riptide trident charged from inside.
     * Not {@code ignoreCancelled}: a click in the air arrives already cancelled, and it
     * is how a pearl is thrown.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || event.useItemInHand() == Event.Result.DENY) {
            return;
        }
        Player player = event.getPlayer();
        Optional<CitadelRules> rules = module.citadelAt(player.getLocation()).map(CitadelDefinition::rules);
        if (rules.isEmpty()) {
            return;
        }
        String refusal = null;
        if (rules.get().partnerItems() && module.isPartnerItem(item)) {
            refusal = EventMessages.CITADEL_NO_PARTNER_ITEMS;
        } else if (rules.get().enderPearls() && item.getType() == Material.ENDER_PEARL) {
            refusal = EventMessages.CITADEL_NO_PEARLS;
        } else if (rules.get().riptide() && item.getType() == Material.TRIDENT
                && item.getEnchantmentLevel(Enchantment.RIPTIDE) > 0) {
            refusal = EventMessages.CITADEL_NO_RIPTIDE;
        }
        if (refusal != null) {
            event.setUseItemInHand(Event.Result.DENY);
            tell(player, refusal);
        }
    }

    /** A chorus fruit is refused before it is eaten, so it is kept. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (event.getItem().getType() != Material.CHORUS_FRUIT) {
            return;
        }
        Player player = event.getPlayer();
        if (module.citadelAt(player.getLocation()).map(citadel -> citadel.rules().chorusFruit()).orElse(false)) {
            event.setCancelled(true);
            tell(player, EventMessages.CITADEL_NO_CHORUS);
        }
    }

    /** Anything else eaten that teleports - a datapack's food - is refused where it would teleport. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.CONSUMABLE_EFFECT) {
            return;
        }
        Player player = event.getPlayer();
        if (module.citadelAt(event.getFrom()).map(citadel -> citadel.rules().chorusFruit()).orElse(false)) {
            event.setCancelled(true);
            tell(player, EventMessages.CITADEL_NO_CHORUS);
        }
    }

    /** No taking off with elytra inside. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onGlide(EntityToggleGlideEvent event) {
        if (!event.isGliding() || !(event.getEntity() instanceof Player player)) {
            return;
        }
        if (module.citadelAt(player.getLocation()).map(citadel -> citadel.rules().elytra()).orElse(false)) {
            event.setCancelled(true);
            tell(player, EventMessages.CITADEL_NO_ELYTRA);
        }
    }

    /**
     * Nor gliding in from outside and out again: a player gliding across the border
     * lands. Only on a change of chunk, since the Citadel is claimed land.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!player.isGliding() || !event.hasChangedBlock()
                || (event.getFrom().getBlockX() >> 4 == event.getTo().getBlockX() >> 4
                    && event.getFrom().getBlockZ() >> 4 == event.getTo().getBlockZ() >> 4)) {
            return;
        }
        if (module.citadelAt(event.getTo()).map(citadel -> citadel.rules().elytra()).orElse(false)) {
            player.setGliding(false);
            tell(player, EventMessages.CITADEL_NO_ELYTRA);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        refusals.forget(event.getPlayer().getUniqueId());
    }

    private void tell(Player player, String key) {
        if (refusals.tryTell(player.getUniqueId(), System.currentTimeMillis())) {
            module.getLang().send(player, key);
        }
    }
}
