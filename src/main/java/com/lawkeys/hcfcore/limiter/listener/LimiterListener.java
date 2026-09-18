package com.lawkeys.hcfcore.limiter.listener;

import com.lawkeys.hcfcore.limiter.ItemLevels;
import com.lawkeys.hcfcore.limiter.LevelCaps;
import com.lawkeys.hcfcore.limiter.LimiterModule;
import com.lawkeys.hcfcore.limiter.LimiterSettings;
import org.bukkit.Bukkit;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.EnumSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Where enchantments and potion effects are made, and where they are brought back
 * down.
 *
 * <p><strong>Enchantments</strong> are capped where they are made - the enchanting
 * table and the anvil - and, unless {@code fix-existing-items} is off, on items that
 * arrive some other way (loot, trades, fishing, other plugins): when their holder
 * joins, closes an inventory, or fights with them. That last net only lowers, and
 * the very first swing of such an item may still land at its old level, since the
 * damage of a hit is worked out before the hit's event is fired.
 *
 * <p><strong>Potion effects</strong> are capped as they are applied, and only when
 * they come from a potion - drunk, splashed, a lingering cloud, a tipped arrow.
 * Golden apples, beacons and other plugins are left alone: a cap on Regeneration is
 * about potions, not about apples.
 */
public final class LimiterListener implements Listener {

    /** The causes a potion cap applies to (EntityPotionEffectEvent.Cause, Paper 26.2). */
    private static final Set<EntityPotionEffectEvent.Cause> FROM_POTIONS = EnumSet.of(
            EntityPotionEffectEvent.Cause.POTION_DRINK,
            EntityPotionEffectEvent.Cause.POTION_SPLASH,
            EntityPotionEffectEvent.Cause.AREA_EFFECT_CLOUD,
            EntityPotionEffectEvent.Cause.ARROW);

    private final LimiterModule module;

    public LimiterListener(LimiterModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    /** @return the enchantment caps in force, or {@code null} when there are none */
    private LevelCaps enchantCaps() {
        LimiterSettings settings = module.getSettings();
        return settings.enabled() && !settings.enchantments().isEmpty() ? settings.enchantments() : null;
    }

    // ------------------------------------------------------------------
    // Enchanting table
    // ------------------------------------------------------------------

    /**
     * Keeps the offers honest. Only cosmetic - the enchantments actually given are
     * decided in {@link #onEnchant} - but an offer reading "Sharpness V" that gives
     * Sharpness II would be a lie. An offer whose hinted enchantment is forbidden is
     * withdrawn; the javadoc allows an offer to be {@code null}.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnchantOffers(PrepareItemEnchantEvent event) {
        LevelCaps caps = enchantCaps();
        if (caps == null) {
            return;
        }
        EnchantmentOffer[] offers = event.getOffers();
        for (int i = 0; i < offers.length; i++) {
            EnchantmentOffer offer = offers[i];
            if (offer == null) {
                continue;
            }
            int allowed = caps.clamp(LimiterModule.key(offer.getEnchantment()), offer.getEnchantmentLevel());
            if (allowed <= 0) {
                offers[i] = null;
            } else if (allowed != offer.getEnchantmentLevel()) {
                offer.setEnchantmentLevel(allowed);
            }
        }
    }

    /**
     * The enchantments the table actually gives. When every one of them is
     * forbidden, enchanting is refused, so the player keeps their levels and lapis
     * rather than paying for an item that comes out bare.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        LevelCaps caps = enchantCaps();
        if (caps == null) {
            return;
        }
        Map<Enchantment, Integer> toAdd = event.getEnchantsToAdd();
        Iterator<Map.Entry<Enchantment, Integer>> entries = toAdd.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<Enchantment, Integer> entry = entries.next();
            int allowed = caps.clamp(LimiterModule.key(entry.getKey()), entry.getValue());
            if (allowed <= 0) {
                entries.remove();
            } else if (allowed != entry.getValue()) {
                entry.setValue(allowed);
            }
        }
        if (toAdd.isEmpty()) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------
    // Anvil
    // ------------------------------------------------------------------

    /**
     * Recomputes every capped level on the anvil's result, which is where a cap
     * above vanilla's maximum takes effect - see {@link LevelCaps#combine}.
     *
     * <p>A result that would gain nothing over the item going in - a forbidden book
     * on a sword, say - is withdrawn, so nobody pays for it.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onAnvil(PrepareAnvilEvent event) {
        LevelCaps caps = enchantCaps();
        ItemStack result = event.getResult();
        if (caps == null || result == null || result.isEmpty()) {
            return;
        }
        ItemStack left = event.getInventory().getFirstItem();
        Map<Enchantment, Integer> leftLevels = ItemLevels.of(left);
        Map<Enchantment, Integer> rightLevels = ItemLevels.of(event.getInventory().getSecondItem());

        ItemStack adjusted = result.clone();
        boolean changed = false;
        for (Map.Entry<Enchantment, Integer> entry : ItemLevels.of(result).entrySet()) {
            Enchantment enchantment = entry.getKey();
            int wanted = caps.combine(LimiterModule.key(enchantment),
                    leftLevels.getOrDefault(enchantment, 0), rightLevels.getOrDefault(enchantment, 0),
                    entry.getValue());
            if (wanted != entry.getValue()) {
                ItemLevels.set(adjusted, enchantment, wanted);
                changed = true;
            }
        }
        if (!changed) {
            return;
        }
        event.setResult(ItemLevels.gainsNothing(left, adjusted) ? null : adjusted);
    }

    // ------------------------------------------------------------------
    // Items that arrived some other way
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        fixInventory(event.getPlayer());
    }

    /**
     * A tick later: the javadoc of {@code InventoryCloseEvent} warns that the
     * inventory is being modified while the event runs.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            Bukkit.getScheduler().runTask(module.getPlugin(), () -> {
                if (player.isOnline()) {
                    fixInventory(player);
                }
            });
        }
    }

    /** The weapon that struck and the armour that was struck, for the next hit. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        LevelCaps caps = fixableCaps();
        if (caps == null) {
            return;
        }
        if (event.getDamager() instanceof Player attacker) {
            ItemStack weapon = attacker.getInventory().getItemInMainHand();
            if (ItemLevels.clampEquipment(weapon, caps)) {
                attacker.getInventory().setItemInMainHand(weapon);
            }
        }
        if (event.getEntity() instanceof Player victim) {
            ItemStack[] armour = victim.getInventory().getArmorContents();
            boolean changed = false;
            for (ItemStack piece : armour) {
                changed |= ItemLevels.clampEquipment(piece, caps);
            }
            if (changed) {
                victim.getInventory().setArmorContents(armour);
            }
        }
    }

    private LevelCaps fixableCaps() {
        LevelCaps caps = enchantCaps();
        return caps != null && module.getSettings().fixExistingItems() ? caps : null;
    }

    /** Brings everything a player carries - worn and held included - down to the caps. */
    public void fixInventory(Player player) {
        LevelCaps caps = fixableCaps();
        if (caps == null) {
            return;
        }
        ItemStack[] contents = player.getInventory().getContents();
        boolean changed = false;
        for (ItemStack item : contents) {
            changed |= ItemLevels.clampEquipment(item, caps);
        }
        if (changed) {
            player.getInventory().setContents(contents);
        }
    }

    // ------------------------------------------------------------------
    // Potions
    // ------------------------------------------------------------------

    /**
     * Refuses a forbidden effect, and brings one above its cap down to it.
     *
     * <p>Bringing it down means refusing this one and applying the capped copy a
     * tick later: the event cannot change the effect it carries, and adding an
     * effect from inside the handler of the one being added is asking for trouble.
     * The capped copy comes from this plugin, so it does not come back through here.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEffect(EntityPotionEffectEvent event) {
        LimiterSettings settings = module.getSettings();
        if (!settings.enabled() || settings.potions().isEmpty()
                || !(event.getEntity() instanceof Player player)
                || !FROM_POTIONS.contains(event.getCause())
                || (event.getAction() != EntityPotionEffectEvent.Action.ADDED
                        && event.getAction() != EntityPotionEffectEvent.Action.CHANGED)) {
            return;
        }
        PotionEffect effect = event.getNewEffect();
        if (effect == null) {
            return;
        }
        int level = effect.getAmplifier() + 1;
        int allowed = settings.potions().clamp(LimiterModule.key(effect.getType()), level);
        if (allowed == level) {
            return;
        }
        event.setCancelled(true);
        if (allowed <= 0) {
            return;
        }
        PotionEffect capped = effect.withAmplifier(allowed - 1);
        Bukkit.getScheduler().runTask(module.getPlugin(), () -> {
            if (player.isOnline()) {
                player.addPotionEffect(capped);
            }
        });
    }
}
