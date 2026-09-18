package com.lawkeys.hcfcore.pvp.listener;

import com.lawkeys.hcfcore.pvp.CombatMath;
import com.lawkeys.hcfcore.pvp.PvpMessages;
import com.lawkeys.hcfcore.pvp.PvpModule;
import com.lawkeys.hcfcore.pvp.PvpModule.Refusal;
import com.lawkeys.hcfcore.pvp.PvpSettings;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionEffectTypeCategory;
import org.bukkit.potion.PotionType;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.Objects;

/**
 * Everything that happens when one player hits another: safe-zone enforcement,
 * combat tagging, the strength nerf and knockback tuning - and what a player may
 * not do to another they could not hit: throw a harmful potion at them, reel them
 * in with a rod, push them with a wind charge ({@link #judge}).
 *
 * <p>Two handlers for the hit, and their priorities say what each does:
 * {@link #onDamage} ({@code HIGH}) <em>decides</em> - a protection refuses the hit,
 * the strength nerf adjusts it - and {@link #onHit} ({@code MONITOR}) <em>records</em>
 * a hit that landed by tagging both sides. The order is written in the priorities
 * rather than left to registration luck. Knockback has its own event, which fires
 * only for a hit that went through.
 */
public final class CombatListener implements Listener {

    private final PvpModule module;

    public CombatListener(PvpModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = resolveAttacker(event);
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }

        // 1. Protections first. A refused hit is cancelled, so onHit never tags for it.
        Refusal refusal = judge(attacker, victim);
        if (refusal != null) {
            event.setCancelled(true);
            refusal.tell(module.getLang(), attacker);
            return;
        }
        PvpSettings settings = module.getSettings();
        if (!settings.enabled()) {
            return;
        }

        // 2. The strength nerf adjusts the damage the server already computed.
        int strengthLevel = getStrengthLevel(attacker);
        if (strengthLevel > 0) {
            event.setDamage(CombatMath.nerfStrength(event.getDamage(), strengthLevel,
                    settings.strength()));
        }
    }

    /**
     * Tags both sides of a hit that landed, telling each only when the tag is new.
     *
     * <p>{@code MONITOR}, cancelled hits ignored: a hit refused after this module has
     * decided - a frozen player's swing, a blow at a staff member in staff mode, both
     * refused at {@code HIGHEST}, or another plugin's rule - must tag nobody. Tagging
     * inside {@link #onDamage}, at {@code HIGH}, tagged both players for 30 seconds on
     * a frozen player's swing that did no damage (found in game, 13/09/2026).
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = resolveAttacker(event);
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        PvpSettings settings = module.getSettings();
        if (!settings.enabled()) {
            return;
        }
        if (module.getCombatTags().tag(victim.getUniqueId())) {
            module.getLang().send(victim, PvpMessages.TAGGED,
                    "seconds", String.valueOf(settings.combatTag().durationSeconds()));
        }
        if (settings.combatTag().tagAttacker()
                && module.getCombatTags().tag(attacker.getUniqueId())) {
            module.getLang().send(attacker, PvpMessages.TAGGED,
                    "seconds", String.valueOf(settings.combatTag().durationSeconds()));
        }
    }

    /**
     * Scales the knockback of a hit between two players.
     *
     * <p>Paper's own knockback event, read in the 26.2 sources: it carries the
     * knockback alone and applies what is set back, so nothing else moving the
     * victim - a fall, a sprint, another plugin - is scaled with it. It replaces
     * scaling the victim's whole velocity a tick later, which was the fallback while
     * the event could not be verified. The subclass fired for an attack,
     * {@code EntityPushedByEntityAttackEvent}, declares no handler list of its own,
     * so it reaches this handler; a hit knocks back more than once when it carries
     * extra knockback (a sprint, the enchantment), and each part is scaled alike.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onKnockback(EntityKnockbackEvent event) {
        PvpSettings settings = module.getSettings();
        if (!settings.enabled() || !settings.knockback().enabled()
                || !(event instanceof EntityPushedByEntityAttackEvent pushed)
                || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = resolveAttacker(pushed.getPushedBy());
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        Vector knockback = event.getKnockback();
        double[] scaled = CombatMath.scaleKnockback(
                new double[] {knockback.getX(), knockback.getY(), knockback.getZ()}, settings.knockback());
        event.setKnockback(new Vector(scaled[0], scaled[1], scaled[2]));
    }

    /**
     * A harmful splash potion does nothing to a player its thrower could not hit.
     *
     * <p>Only a potion with a harmful effect: a potion of healing thrown at spawn
     * still heals everyone it reaches. One with a harmful effect among good ones - a
     * turtle master's slowness - is held back whole, since a potion's intensity is
     * set per player, not per effect. An intensity of 0 applies nothing: Paper scales
     * both the instant part and the duration by it (26.2 sources).
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onSplash(PotionSplashEvent event) {
        ThrownPotion potion = event.getPotion();
        if (!(potion.getShooter() instanceof Player thrower) || !isHarmful(potion.getEffects())) {
            return;
        }
        Refusal first = null;
        for (LivingEntity entity : event.getAffectedEntities()) {
            if (entity instanceof Player victim && !victim.getUniqueId().equals(thrower.getUniqueId())) {
                Refusal refusal = judge(thrower, victim);
                if (refusal != null) {
                    event.setIntensity(victim, 0);
                    first = first == null ? refusal : first;
                }
            }
        }
        if (first != null) {
            first.tell(module.getLang(), thrower);
        }
    }

    /**
     * The same for a lingering potion's cloud, each time it applies. No message: a
     * cloud applies several times a second.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onCloud(AreaEffectCloudApplyEvent event) {
        AreaEffectCloud cloud = event.getEntity();
        if (!(cloud.getSource() instanceof Player thrower) || !isHarmful(cloud)) {
            return;
        }
        // The javadoc calls this list mutable.
        event.getAffectedEntities().removeIf(entity -> entity instanceof Player victim
                && !victim.getUniqueId().equals(thrower.getUniqueId())
                && judge(thrower, victim) != null);
    }

    /**
     * A fishing rod does not pull a player its angler could not hit - out of spawn,
     * typically. Paper fires this before the pull and skips it when cancelled (26.2
     * sources).
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onReel(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_ENTITY || !(event.getCaught() instanceof Player victim)) {
            return;
        }
        Player angler = event.getPlayer();
        if (victim.getUniqueId().equals(angler.getUniqueId())) {
            return;
        }
        Refusal refusal = judge(angler, victim);
        if (refusal != null) {
            event.setCancelled(true);
            refusal.tell(module.getLang(), angler);
        }
    }

    /**
     * A player's blast - a wind charge, or TNT or a crystal they set off - does not
     * push a player they could not hit.
     *
     * <p>Paper reports a blast's push player by player and names as the one pushing
     * whoever the blast is credited to: the thrower of a wind charge (26.2 sources).
     * The wind charge's direct hit is damage, refused by {@link #onDamage}. No
     * message: one blast pushes everyone around it.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBlastPush(EntityKnockbackEvent event) {
        if (event.getCause() != EntityKnockbackEvent.Cause.EXPLOSION
                || !(event instanceof EntityPushedByEntityAttackEvent pushed)
                || !(event.getEntity() instanceof Player victim)
                || !(pushed.getPushedBy() instanceof Player attacker)
                || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        if (judge(attacker, victim) != null) {
            event.setCancelled(true);
        }
    }

    /**
     * Whether this attacker may harm this victim at all - see
     * {@link PvpModule#judgeHarm}, where the rules live so that anything else able to
     * reach a player (a Bard's debuff, for one) follows the same ones.
     *
     * @return why not, or {@code null} when the harm may land
     */
    private Refusal judge(Player attacker, Player victim) {
        return module.judgeHarm(attacker, victim).orElse(null);
    }

    private static boolean isHarmful(Collection<PotionEffect> effects) {
        for (PotionEffect effect : effects) {
            if (effect.getType().getCategory() == PotionEffectTypeCategory.HARMFUL) {
                return true;
            }
        }
        return false;
    }

    private static boolean isHarmful(AreaEffectCloud cloud) {
        PotionType base = cloud.getBasePotionType();
        return (base != null && isHarmful(base.getPotionEffects()))
                || (cloud.hasCustomEffects() && isHarmful(cloud.getCustomEffects()));
    }

    /**
     * @return the player a hit is credited to - the attacker, the shooter of a
     *         projectile, whoever lit the TNT or struck the crystal - or {@code null}
     *         for anything else
     *
     * <p>The damage source's causing entity, which its javadoc defines as "the entity
     * to which the damage is ultimately attributed if the receiver is killed". Only
     * the damager used to be read, with projectiles followed back by hand: a crystal
     * or a TNT is its own damager, so its blast was credited to nobody and judged by
     * no rule - during SOTW a player killed another with a crystal in the wilderness,
     * and a teammate could too (found in the listener review; the project owner chose
     * to credit it, 15/09/2026). A bed or a respawn anchor set off in the wrong
     * dimension names no entity at all, and stays uncredited.
     */
    private static Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        return event.getDamageSource().getCausingEntity() instanceof Player causing ? causing : null;
    }

    /**
     * @return the player behind a push, following a projectile back to whoever fired
     *         it; {@code null} for anything else
     */
    private static Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    /**
     * Reads the attacker's Strength level.
     *
     * <p>Through {@code PotionEffectType.STRENGTH}, which the Paper 26.2 sources
     * declare with the key {@code strength}. It used to match the effect's
     * {@code toString()} against a configured list of spellings, because the
     * constant had been renamed across versions and could not be verified; but
     * {@code toString()} is implemented by the server and documented nowhere, so the
     * nerf could have silently never applied.
     *
     * @return the level, where amplifier 0 means level 1, or {@code 0} for none
     */
    private int getStrengthLevel(Player attacker) {
        if (!module.getSettings().strength().enabled()) {
            return 0;
        }
        PotionEffect strength = attacker.getPotionEffect(PotionEffectType.STRENGTH);
        return strength == null ? 0 : strength.getAmplifier() + 1;
    }
}
