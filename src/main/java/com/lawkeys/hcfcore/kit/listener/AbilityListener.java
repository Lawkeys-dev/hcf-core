package com.lawkeys.hcfcore.kit.listener;

import com.lawkeys.hcfcore.kit.Ability;
import com.lawkeys.hcfcore.kit.KitMessages;
import com.lawkeys.hcfcore.kit.KitModule;
import com.lawkeys.hcfcore.util.Durations;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/** Partner items: right-click to use, with a per-player cooldown. */
public final class AbilityListener implements Listener {

    private final KitModule module;

    public AbilityListener(KitModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    /**
     * At {@code LOW}: after a freeze, which refuses the click at {@code LOWEST}, and
     * before territory protection at {@code NORMAL}, so a partner item still works
     * when it happens to be clicked on a block of protected land - it cancels the
     * block use itself below.
     *
     * <p>Not {@code ignoreCancelled}: a click on air arrives already cancelled (there
     * is no block to use), yet it is exactly how most abilities are used. What is
     * read instead is whether somebody refused the <em>item</em>.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.useItemInHand() == Event.Result.DENY) {
            return;
        }
        if (!module.getSettings().enabled()) {
            return;
        }
        Ability ability = module.abilityOf(event.getItem());
        if (ability == null) {
            return;
        }
        // Cancelled whatever happens next: a partner item must never also place a
        // block or open a chest.
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            // The offhand fires a second event for the same click.
            return;
        }
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        long remaining = module.getAbilityCooldowns().remaining(player.getUniqueId(), ability.id(), now);
        if (remaining > 0) {
            module.getLang().send(player, KitMessages.ABILITY_COOLDOWN,
                    "ability", ability.id(), "time", Durations.formatWithSeconds(remaining));
            return;
        }
        if (!module.getAbilityCooldowns().tryUse(player.getUniqueId(), ability.id(), ability.cooldownSeconds(), now)) {
            return;
        }
        if (ability.consume()) {
            ItemStack used = event.getItem();
            if (used != null) {
                used.setAmount(used.getAmount() - 1);
            }
        }
        for (String command : ability.commandsFor(player.getName())) {
            // From the console, so an ability can do what its holder could not.
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
        module.getLang().send(player, KitMessages.ABILITY_USED, "ability", ability.id());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        module.getAbilityCooldowns().forget(event.getPlayer().getUniqueId());
    }
}
