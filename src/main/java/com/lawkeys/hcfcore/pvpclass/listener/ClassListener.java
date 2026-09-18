package com.lawkeys.hcfcore.pvpclass.listener;

import com.lawkeys.hcfcore.pvpclass.ArcherTag;
import com.lawkeys.hcfcore.pvpclass.Backstab;
import com.lawkeys.hcfcore.pvpclass.ClassMessages;
import com.lawkeys.hcfcore.pvpclass.ClassModule;
import com.lawkeys.hcfcore.pvpclass.DyesMenu;
import com.lawkeys.hcfcore.pvpclass.PvpClass;
import com.lawkeys.hcfcore.util.Durations;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What classes do when players click and fight.
 *
 * <p>Damage is handled in two steps, and the priorities say which does what:
 * {@link #onHit} ({@code HIGHEST}) <em>decides</em> - after {@code pvp/} has refused
 * what it refuses at {@code HIGH}, so a hit that may not land is never a backstab -
 * and {@link #onLanded} ({@code MONITOR}) <em>records</em> a hit that went through:
 * the backstab's damage, its broken weapon, the archer tag, an arrow's dye effect.
 */
public final class ClassListener implements Listener {

    private static final String BACKSTAB_COOLDOWN = "backstab";

    private final ClassModule module;
    /** Backstabs decided at {@code HIGHEST}, applied at {@code MONITOR} if the hit lands. Victim to attacker. */
    private final Map<UUID, PendingBackstab> pendingBackstabs = new ConcurrentHashMap<>();

    /** @param lethal dealt as the hit itself, which kills: nothing is left to take off */
    private record PendingBackstab(UUID attacker, Backstab backstab, boolean lethal) {
    }

    public ClassListener(ClassModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    /**
     * The hand changed: its item's held effect comes at once, not at the next
     * renewal - a Bard scrolls over an item for a fraction of a second.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        module.pulseHeld(event.getPlayer(), event.getPlayer().getInventory().getItem(event.getNewSlot()));
    }

    /**
     * A right-click with a class's item. At {@code LOW}, like partner items: after a
     * freeze refuses the click at {@code LOWEST}, and before territory protection. Not
     * {@code ignoreCancelled}: a click in the air arrives already cancelled, yet it is
     * how these items are used - what is read instead is whether somebody refused the
     * item.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.useItemInHand() == Event.Result.DENY || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (module.click(event.getPlayer(), event.getItem())) {
            // Used or refused, the item does nothing else: a spider eye is not eaten.
            event.setCancelled(true);
        }
    }

    /**
     * Decides a hit between two players: a backstab, or more damage for an
     * archer-tagged victim.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        pendingBackstabs.remove(victim.getUniqueId());
        Player attacker = creditedPlayer(event);
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())
                || !module.getSettings().enabled()) {
            return;
        }
        if (event.getDamager() instanceof Player && tryBackstab(event, attacker, victim)) {
            return;
        }
        double multiplier = module.getArcherTags().multiplier(victim.getUniqueId(), System.currentTimeMillis());
        if (multiplier > 1.0) {
            event.setDamage(event.getDamage() * multiplier);
        }
    }

    /**
     * A melee hit from behind with the class's weapon.
     *
     * <p>The backstab's damage goes through armour, enchantments and effects, as the
     * Rogue's does on every HCF server: the hit itself deals nothing, and the damage
     * is taken off the victim's health once the hit has landed. A backstab that
     * would kill is dealt as a normal, overwhelming hit instead, so the death is the
     * attacker's kill with everything that follows it - deathban, DTR, statistics.
     *
     * @return whether the hit is a backstab
     */
    private boolean tryBackstab(EntityDamageByEntityEvent event, Player attacker, Player victim) {
        Optional<PvpClass> active = module.getManager().active(attacker.getUniqueId());
        if (active.isEmpty() || active.get().backstab() == null) {
            return false;
        }
        Backstab backstab = active.get().backstab();
        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (!weapon.getType().name().equals(backstab.weapon())) {
            return false;
        }
        Location from = victim.getLocation();
        Location by = attacker.getLocation();
        if (!backstab.isBehind(from.getX(), from.getZ(), from.getYaw(), by.getX(), by.getZ(), by.getYaw())) {
            return false;
        }
        long now = System.currentTimeMillis();
        long wait = module.getCooldowns().remaining(attacker.getUniqueId(), BACKSTAB_COOLDOWN, now);
        if (wait > 0) {
            module.getLang().send(attacker, ClassMessages.BACKSTAB_COOLDOWN,
                    "time", Durations.formatWithSeconds(wait));
            return false;
        }
        double health = victim.getHealth() + victim.getAbsorptionAmount();
        boolean lethal = backstab.damage() >= health;
        if (lethal) {
            // Armour cannot bring this under what is left: the ordinary hit kills.
            event.setDamage(Math.max(event.getDamage(), health) * 1000.0);
        } else {
            event.setDamage(0.0);
        }
        pendingBackstabs.put(victim.getUniqueId(), new PendingBackstab(attacker.getUniqueId(), backstab, lethal));
        return true;
    }

    /**
     * Records a hit that landed: applies a backstab decided above, and archer-tags
     * the victim of a class's arrow. Not {@code ignoreCancelled}, so a backstab
     * whose hit was refused after {@link #onHit} is dropped rather than left pending.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onLanded(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        PendingBackstab backstab = pendingBackstabs.remove(victim.getUniqueId());
        if (event.isCancelled()) {
            return;
        }
        Player attacker = creditedPlayer(event);
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        if (backstab != null && backstab.attacker().equals(attacker.getUniqueId())) {
            landBackstab(attacker, victim, backstab.backstab(), backstab.lethal());
        }
        if (event.getDamager() instanceof AbstractArrow) {
            tagWithArrow(attacker, victim);
            if (module.getSettings().enabled()) {
                module.onArrowHit(attacker, victim);
            }
        }
    }

    private void landBackstab(Player attacker, Player victim, Backstab backstab, boolean lethal) {
        long now = System.currentTimeMillis();
        module.getCooldowns().start(attacker.getUniqueId(), BACKSTAB_COOLDOWN, backstab.cooldownSeconds(), now);
        double left = lethal ? 0 : backstab.damage();
        // Through absorption first, then health - what armour would have reduced.
        double absorption = victim.getAbsorptionAmount();
        if (absorption > 0) {
            double taken = Math.min(absorption, left);
            victim.setAbsorptionAmount(absorption - taken);
            left -= taken;
        }
        if (left > 0 && !victim.isDead()) {
            // Not lethal, as decided above: this leaves the victim standing.
            victim.setHealth(Math.max(0.5, victim.getHealth() - left));
        }
        if (backstab.breakWeapon()) {
            attacker.getInventory().setItemInMainHand(null);
            attacker.playSound(attacker.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
        }
        module.getLang().send(attacker, ClassMessages.BACKSTAB_ATTACKER, "player", victim.getName());
        module.getLang().send(victim, ClassMessages.BACKSTAB_VICTIM, "player", attacker.getName());
    }

    private void tagWithArrow(Player shooter, Player victim) {
        Optional<PvpClass> active = module.getManager().active(shooter.getUniqueId());
        if (active.isEmpty() || active.get().archerTag() == null || !module.getSettings().enabled()) {
            return;
        }
        ArcherTag tag = active.get().archerTag();
        module.getArcherTags().tag(victim.getUniqueId(), tag, System.currentTimeMillis());
        String seconds = String.valueOf(tag.seconds());
        String percent = String.valueOf(tag.percent());
        module.getLang().send(shooter, ClassMessages.ARCHER_TAGGED_SHOOTER,
                "player", victim.getName(), "seconds", seconds, "percent", percent);
        module.getLang().send(victim, ClassMessages.ARCHER_TAGGED_VICTIM,
                "player", shooter.getName(), "seconds", seconds, "percent", percent);
    }

    /**
     * @return the player a hit is credited to - the attacker, or the shooter of an
     *         arrow - or {@code null}: the damage source's causing entity, as
     *         {@code pvp/} reads it
     */
    private static Player creditedPlayer(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        return event.getDamageSource().getCausingEntity() instanceof Player causing ? causing : null;
    }

    /** The {@code /dyes} menu is read only. Recognised by its holder, read with {@code getHolder(false)}. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder(false) instanceof DyesMenu) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder(false) instanceof DyesMenu) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        pendingBackstabs.remove(event.getPlayer().getUniqueId());
        module.onQuit(event.getPlayer());
    }

    /** A cancelled death - a player revived - keeps the class (CONTRIBUTING.md section 7). */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        module.onDeath(event.getPlayer());
    }
}
