package com.lawkeys.hcfcore.pvp.legacy;

import com.lawkeys.hcfcore.pvp.PvpMessages;
import com.lawkeys.hcfcore.pvp.PvpModule;
import com.lawkeys.hcfcore.pvp.PvpSettings;
import com.lawkeys.hcfcore.pvp.legacy.LegacyCombatSettings.Apple;
import com.lawkeys.hcfcore.pvp.legacy.LegacyCombatSettings.AppleEffect;
import com.lawkeys.hcfcore.util.RefusalThrottle;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.BlocksAttacks;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import io.papermc.paper.datacomponent.item.blocksattacks.DamageReduction;
import io.papermc.paper.datacomponent.item.blocksattacks.ItemDamageFunction;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The 1.7.10 combat (config.yml, {@code combat: classic}): each handler asks the
 * module for the classic settings, and does nothing in {@code modern}.
 *
 * <p>What a player may not harm is decided before any of this, by
 * {@link PvpModule#judgeHarm}: the handlers that shape a hit run after it has been
 * let through ({@code ignoreCancelled}), and the fishing rod asks the judge itself.
 */
public final class LegacyCombatListener implements Listener {

    private static final long MESSAGE_EVERY_MILLIS = 2_000L;
    /** The attack modifier classic combat puts on a weapon; how its own is told from any other. */
    private static final NamespacedKey WEAPON_DAMAGE_KEY =
            Objects.requireNonNull(NamespacedKey.fromString("hcfcore:legacy_damage"));
    /** A player's own attack damage, which a weapon's modifier adds to. */
    private static final double PLAYER_BASE_DAMAGE = 1.0;

    private final PvpModule module;
    private final RefusalThrottle refusals = new RefusalThrottle(MESSAGE_EVERY_MILLIS);
    /** When each player's current stretch of regeneration began; main thread only. */
    private final Map<UUID, Long> regenerating = new HashMap<>();
    /** Victims of a melee hit that landed this tick, whose next push is the hit's own; main thread only. */
    private final java.util.Set<UUID> awaitingBasePush = new java.util.HashSet<>();

    public LegacyCombatListener(PvpModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    private Optional<LegacyCombatSettings> classic() {
        return module.classicCombat();
    }

    // ------------------------------------------------------------------
    // Hits: no sweeping, critical hits
    // ------------------------------------------------------------------

    /** 1.7 had no sweep attack: the sweep's damage to the players around is refused. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onSweep(EntityDamageByEntityEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK
                && classic().map(LegacyCombatSettings::noSweepAttacks).orElse(false)) {
            event.setCancelled(true);
        }
    }

    /**
     * And the sweep's push: the game pushes the players around before it hurts them
     * (26.2 sources, {@code Player#attack}, cause {@code SWEEP_ATTACK}), so refusing
     * the damage alone would leave the push.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onSweepPush(EntityKnockbackEvent event) {
        if (event.getCause() == EntityKnockbackEvent.Cause.SWEEP_ATTACK
                && classic().map(LegacyCombatSettings::noSweepAttacks).orElse(false)) {
            event.setCancelled(true);
        }
    }

    /**
     * A critical hit by 1.7's rules on a mob - sprinting included, which the modern
     * game refuses. A blow on a player is rebuilt whole by {@link #rebuildMelee}.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onCritical(EntityDamageByEntityEvent event) {
        if (event.isCritical() || !(event.getDamager() instanceof Player attacker)
                || !(event.getEntity() instanceof LivingEntity victim) || victim instanceof Player
                || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            return;
        }
        Optional<LegacyCombatSettings> settings = classic().filter(c -> c.criticals().enabled());
        if (settings.isPresent() && fallingHit(attacker)) {
            event.setDamage(event.getDamage() * settings.get().criticals().multiplier());
            critParticles(victim);
        }
    }

    /**
     * A player's blow on a player, rebuilt the 1.7 way ({@link LegacyMath#rebuildHit}):
     * the weapon's damage, Strength (1.7's percentage, nerfed or not; else the
     * modern flat bonus, nerfed or not), a critical by 1.7's rules, then Sharpness. Called by the combat
     * listener where Strength has always been adjusted. A mace is left as the modern
     * game hits: its fall bonus is no weapon damage 1.7 knew.
     *
     * @return the blow's damage
     */
    public static double rebuildMelee(EntityDamageByEntityEvent event, Player attacker, int strengthLevel,
                                      LegacyCombatSettings classic, PvpSettings.StrengthRules nerf) {
        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (weapon.getType() == Material.MACE) {
            return event.getDamage();
        }
        LegacyMath.StrengthRule strength;
        if (classic.strength().enabled()) {
            strength = new LegacyMath.StrengthRule(true, classic.strength().effectivePerLevel());
        } else if (nerf.enabled()) {
            strength = new LegacyMath.StrengthRule(false, nerf.nerfedBonusPerLevel());
        } else {
            strength = new LegacyMath.StrengthRule(false, nerf.vanillaBonusPerLevel());
        }
        boolean legacyCritical = classic.criticals().enabled() && !event.isCritical() && fallingHit(attacker);
        boolean critical = event.isCritical() || legacyCritical;
        double multiplier = classic.criticals().enabled() ? classic.criticals().multiplier() : LegacyMath.MODERN_CRITICAL;
        java.util.OptionalDouble sharpness = classic.enchantments().enabled()
                ? java.util.OptionalDouble.of(classic.enchantments().sharpnessPerLevel())
                : java.util.OptionalDouble.empty();
        if (legacyCritical) {
            critParticles(event.getEntity());
        }
        return LegacyMath.rebuildHit(event.getDamage(), event.isCritical(), strengthLevel,
                nerf.vanillaBonusPerLevel(), strength, critical, multiplier,
                weapon.getEnchantmentLevel(Enchantment.SHARPNESS), sharpness);
    }

    /**
     * Whether a blow is critical by 1.7's rules: falling, not standing, climbing,
     * swimming, blind or riding - sprinting or not. Standing is read from the block
     * under the feet, not from Player#isOnGround, which Paper deprecates: it is what
     * the client claims.
     */
    private static boolean fallingHit(Player attacker) {
        boolean standing = attacker.getLocation().subtract(0, 0.05, 0).getBlock().isCollidable();
        return LegacyMath.isCritical(attacker.getFallDistance(), standing,
                attacker.isClimbing(), attacker.isInWater(), attacker.hasPotionEffect(PotionEffectType.BLINDNESS),
                attacker.isInsideVehicle());
    }

    private static void critParticles(org.bukkit.entity.Entity victim) {
        Location at = victim.getLocation().add(0, victim.getHeight() / 2, 0);
        victim.getWorld().spawnParticle(Particle.CRIT, at, 12, 0.3, 0.4, 0.3, 0.1);
    }

    // ------------------------------------------------------------------
    // Knockback
    // ------------------------------------------------------------------

    /**
     * Marks the victim of a melee hit that landed: the next push it gets is the hit's
     * own. A melee hit pushes up to twice, and Paper names both pushes
     * {@code ENTITY_ATTACK} (26.2 sources: {@code LivingEntity#hurtServer} deals the
     * hit's push, with {@code DAMAGE} only when there is no direct attacker, then
     * {@code Player#attack} adds a sprint or enchantment push with
     * {@code causeExtraKnockback}) - but always in that order, the hit's first, and
     * after this event. The mark lasts until the next tick.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onHitLanded(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player && event.getEntity() instanceof LivingEntity victim
                && event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && classic().map(c -> c.knockback().enabled()).orElse(false)) {
            UUID id = victim.getUniqueId();
            if (awaitingBasePush.add(id)) {
                Bukkit.getScheduler().runTask(module.getPlugin(), () -> awaitingBasePush.remove(id));
            }
        }
    }

    /**
     * 1.7.10 knockback for a melee hit. Before the knockback multipliers of
     * {@code pvp.yml} ({@code HIGH}), which then scale the 1.7 push like any other.
     *
     * <p>Paper hands the push as what is added to the victim's velocity (26.2
     * sources, {@code LivingEntity#knockback}): the 1.7 velocity minus the current one.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onKnockback(EntityKnockbackEvent event) {
        if (!(event instanceof EntityPushedByEntityAttackEvent pushed)
                || !(pushed.getPushedBy() instanceof Player attacker)
                || !(event.getEntity() instanceof LivingEntity victim)
                || event.getCause() != EntityKnockbackEvent.Cause.ENTITY_ATTACK) {
            return;
        }
        Optional<LegacyCombatSettings.Knockback> rules = classic().map(LegacyCombatSettings::knockback)
                .filter(LegacyCombatSettings.Knockback::enabled);
        if (rules.isEmpty()) {
            return;
        }
        if (awaitingBasePush.remove(victim.getUniqueId())) {
            LegacyMath.Velocity current = velocity(victim.getVelocity());
            LegacyMath.Velocity wanted = LegacyMath.knockback(current,
                    attacker.getLocation().getX() - victim.getLocation().getX(),
                    attacker.getLocation().getZ() - victim.getLocation().getZ(),
                    knockbackResistance(victim), rules.get());
            event.setKnockback(vector(wanted.minus(current)));
        } else {
            int level = attacker.getInventory().getItemInMainHand().getEnchantmentLevel(Enchantment.KNOCKBACK)
                    + (attacker.isSprinting() ? 1 : 0);
            event.setKnockback(vector(LegacyMath.extraKnockback(attacker.getLocation().getYaw(), level, rules.get())));
        }
    }

    private static double knockbackResistance(LivingEntity entity) {
        AttributeInstance attribute = entity.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        return attribute == null ? 0.0 : attribute.getValue();
    }

    private static LegacyMath.Velocity velocity(Vector vector) {
        return new LegacyMath.Velocity(vector.getX(), vector.getY(), vector.getZ());
    }

    private static Vector vector(LegacyMath.Velocity velocity) {
        return new Vector(velocity.x(), velocity.y(), velocity.z());
    }

    // ------------------------------------------------------------------
    // Weapon damage, sword blocking, no off-hand, no shields
    // ------------------------------------------------------------------

    /**
     * Gives the items a player holds what classic combat puts on them - a sword's
     * ability to block, the way a shield does (Paper's {@code blocks_attacks} item
     * component, 26.2), and a weapon's 1.7 damage - or takes it back when classic
     * combat or the part is off. Only the items in hand: those are the ones that can
     * be used, and this runs whenever the hand changes and twice a second.
     */
    public void syncHands(Player player) {
        Optional<LegacyCombatSettings> settings = classic();
        boolean block = settings.map(c -> c.swordBlocking().enabled()).orElse(false);
        PlayerInventory inventory = player.getInventory();
        syncSword(inventory.getItemInMainHand(), block ? settings.get().swordBlocking() : null);
        syncWeapon(inventory.getItemInMainHand(), weaponDamage(settings));
        if (settings.map(LegacyCombatSettings::disableOffhand).orElse(false)) {
            ItemStack offhand = inventory.getItemInOffHand();
            if (!offhand.isEmpty()) {
                inventory.setItemInOffHand(null);
                inventory.addItem(offhand).values()
                        .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
            }
        } else {
            syncSword(inventory.getItemInOffHand(), block ? settings.get().swordBlocking() : null);
        }
    }

    private static void syncSword(ItemStack item, LegacyCombatSettings.SwordBlocking blocking) {
        if (item == null || item.isEmpty() || !item.getType().name().endsWith("_SWORD")) {
            return;
        }
        boolean has = item.hasData(DataComponentTypes.BLOCKS_ATTACKS);
        if (blocking == null) {
            if (has) {
                // Back to what a sword is by default: no blocking.
                item.resetData(DataComponentTypes.BLOCKS_ATTACKS);
            }
            return;
        }
        if (has) {
            return;
        }
        item.setData(DataComponentTypes.BLOCKS_ATTACKS, BlocksAttacks.blocksAttacks()
                .blockDelaySeconds(0f)
                .disableCooldownScale(0f)
                .addDamageReduction(DamageReduction.damageReduction()
                        .horizontalBlockingAngle(180f)
                        .base((float) blocking.base())
                        .factor((float) blocking.factor())
                        .build())
                // A sword did not wear out from blocking.
                .itemDamage(ItemDamageFunction.itemDamageFunction().threshold(Float.MAX_VALUE).base(0f).factor(0f)
                        .build())
                .build());
    }

    private static LegacyCombatSettings.WeaponDamage weaponDamage(Optional<LegacyCombatSettings> settings) {
        return settings.map(LegacyCombatSettings::weaponDamage).filter(LegacyCombatSettings.WeaponDamage::enabled)
                .orElse(null);
    }

    /**
     * Gives a weapon its 1.7 damage: the item's own attribute modifiers, its attack
     * damage replaced by one of classic combat's - the attack speed and anything else
     * kept. The tooltip shows it, and a critical hit or Strength count from it as
     * from any weapon's. A weapon whose modifiers someone else changed (a kit's, another
     * plugin's) is left as it is; one classic combat changed goes back to its
     * default when the part is off, or the item is not listed.
     */
    private static void syncWeapon(ItemStack item, LegacyCombatSettings.WeaponDamage weapons) {
        if (item == null || item.isEmpty()) {
            return;
        }
        boolean overridden = item.isDataOverridden(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        ItemAttributeModifiers current = item.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        AttributeModifier ours = current == null ? null : current.modifiers().stream()
                .map(ItemAttributeModifiers.Entry::modifier)
                .filter(modifier -> WEAPON_DAMAGE_KEY.equals(modifier.getKey()))
                .findFirst().orElse(null);
        if (overridden && ours == null) {
            return;
        }
        java.util.OptionalDouble damage = weapons == null
                ? java.util.OptionalDouble.empty() : weapons.of(item.getType().getKey().getKey());
        if (damage.isEmpty()) {
            if (ours != null) {
                item.resetData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
            }
            return;
        }
        double amount = damage.getAsDouble() - PLAYER_BASE_DAMAGE;
        if (ours != null && ours.getAmount() == amount) {
            return;
        }
        ItemAttributeModifiers defaults = item.getType().getDefaultData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.itemAttributes();
        if (defaults != null) {
            for (ItemAttributeModifiers.Entry entry : defaults.modifiers()) {
                if (entry.attribute() != Attribute.ATTACK_DAMAGE) {
                    builder.addModifier(entry.attribute(), entry.modifier(), entry.getGroup(), entry.display());
                }
            }
        }
        builder.addModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(WEAPON_DAMAGE_KEY, amount,
                AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        item.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, builder.build());
    }

    /**
     * Takes back from every item a player carries what classic combat put on it -
     * blocking, weapon damage: classic combat is being switched off.
     */
    public static void stripItems(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            syncSword(item, null);
            syncWeapon(item, null);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        // The new slot's item, now: by the next tick the player may already block.
        ItemStack next = player.getInventory().getItem(event.getNewSlot());
        Optional<LegacyCombatSettings> settings = classic();
        syncSword(next, settings.filter(c -> c.swordBlocking().enabled())
                .map(LegacyCombatSettings::swordBlocking).orElse(null));
        syncWeapon(next, weaponDamage(settings));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        syncHands(event.getPlayer());
        syncRegeneration(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (classic().map(LegacyCombatSettings::disableOffhand).orElse(false)) {
            event.setCancelled(true);
            tell(event.getPlayer(), PvpMessages.LEGACY_NO_OFFHAND);
        }
    }

    /** No item into the off-hand slot through the inventory either - a click, a key, or a drag. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!classic().map(LegacyCombatSettings::disableOffhand).orElse(false)
                || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        boolean offhandSlot = event.getClickedInventory() instanceof PlayerInventory
                && event.getSlot() == OFFHAND_SLOT;
        if (offhandSlot || event.getClick() == ClickType.SWAP_OFFHAND) {
            event.setCancelled(true);
            tell(player, PvpMessages.LEGACY_NO_OFFHAND);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (classic().map(LegacyCombatSettings::disableOffhand).orElse(false)
                && event.getInventory().getType() == org.bukkit.event.inventory.InventoryType.CRAFTING
                && event.getInventorySlots().contains(OFFHAND_SLOT)) {
            event.setCancelled(true);
        }
    }

    /** The off-hand slot's index in a player's inventory (Bukkit's {@code PlayerInventory} layout). */
    private static final int OFFHAND_SLOT = 40;

    /** 1.7 had no shields: raising one is refused. Before partner items and classes ({@code LOW}). */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onUseShield(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item != null && item.getType() == Material.SHIELD
                && classic().map(LegacyCombatSettings::disableShields).orElse(false)) {
            event.setUseItemInHand(Event.Result.DENY);
            tell(event.getPlayer(), PvpMessages.LEGACY_NO_SHIELD);
        }
    }

    // ------------------------------------------------------------------
    // Thrown potions and ender pearls
    // ------------------------------------------------------------------

    /**
     * A splash potion or an ender pearl leaves the hand as it did in 1.7: straight
     * where the player looks, without the player's own movement added.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onThrow(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof Player player)) {
            return;
        }
        Optional<LegacyCombatSettings> settings = classic();
        if (settings.isEmpty()) {
            return;
        }
        LegacyCombatSettings.Throw rules = null;
        if (projectile instanceof ThrownPotion) {
            rules = settings.get().potions();
        } else if (projectile instanceof EnderPearl) {
            rules = settings.get().pearls().throwing();
            if (settings.get().pearls().noCooldown()) {
                // The game starts the pearl's cooldown after this event: clear it a tick later.
                Bukkit.getScheduler().runTask(module.getPlugin(),
                        () -> player.setCooldown(ItemStack.of(Material.ENDER_PEARL), 0));
            }
        }
        if (rules == null || !rules.enabled()) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location eye = player.getLocation();
        LegacyMath.Velocity velocity = LegacyMath.throwVelocity(eye.getYaw(), eye.getPitch(), rules,
                new double[] {random.nextGaussian(), random.nextGaussian(), random.nextGaussian()});
        projectile.setVelocity(vector(velocity));
    }

    // ------------------------------------------------------------------
    // Natural regeneration
    // ------------------------------------------------------------------

    /**
     * The game's own regeneration rates, while classic regeneration holds them off:
     * set so high the food bar never heals. A cancelled heal is not enough - the
     * game charges its hunger all the same (26.2 {@code FoodData#tick} calls
     * {@code causeFoodExhaustion} after {@code heal}, cancelled or not), so a hurt
     * player's saturation drained fast while {@link #regenerate} healed slowly.
     */
    private static final int HELD_OFF_RATE = 1_000_000_000;
    /** The game's defaults (Paper 26.2 {@code HumanEntity} javadoc): 10 ticks saturated, 80 unsaturated. */
    private static final int SATURATED_RATE = 10;
    private static final int UNSATURATED_RATE = 80;

    /**
     * Holds the game's own regeneration off while classic regeneration is on, and
     * gives it back otherwise - only rates this listener set, so another plugin's
     * stay as they are.
     */
    public void syncRegeneration(Player player) {
        boolean classicRegen = classic().map(c -> c.regeneration().enabled()).orElse(false);
        if (classicRegen) {
            player.setSaturatedRegenRate(HELD_OFF_RATE);
            player.setUnsaturatedRegenRate(HELD_OFF_RATE);
        } else {
            restoreRegeneration(player);
        }
    }

    /** Gives the game its own regeneration back, if classic combat held it off. */
    public static void restoreRegeneration(Player player) {
        if (player.getSaturatedRegenRate() == HELD_OFF_RATE) {
            player.setSaturatedRegenRate(SATURATED_RATE);
        }
        if (player.getUnsaturatedRegenRate() == HELD_OFF_RATE) {
            player.setUnsaturatedRegenRate(UNSATURATED_RATE);
        }
    }

    /** The modern regeneration, refused as well should another path heal: {@link #regenerate} heals instead. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onRegain(EntityRegainHealthEvent event) {
        if (event.getEntity() instanceof Player
                && event.getRegainReason() == EntityRegainHealthEvent.RegainReason.SATIATED
                && classic().map(c -> c.regeneration().enabled()).orElse(false)) {
            event.setCancelled(true);
        }
    }

    /**
     * 1.7.10's natural regeneration, every half-second: a player whose food is high
     * enough, and who is hurt, heals {@code amount} each time {@code interval-seconds}
     * have passed that way - the count starts over as soon as they are not. Only
     * where the world lets health regenerate by itself.
     */
    public void regenerate() {
        Optional<LegacyCombatSettings.Regeneration> rules = classic().map(LegacyCombatSettings::regeneration)
                .filter(LegacyCombatSettings.Regeneration::enabled);
        if (rules.isEmpty()) {
            regenerating.clear();
            Bukkit.getOnlinePlayers().forEach(LegacyCombatListener::restoreRegeneration);
            return;
        }
        long now = System.currentTimeMillis();
        long interval = Math.round(rules.get().intervalSeconds() * 1000.0);
        for (Player player : Bukkit.getOnlinePlayers()) {
            syncRegeneration(player);
            AttributeInstance maximum = player.getAttribute(Attribute.MAX_HEALTH);
            double max = maximum == null ? 20.0 : maximum.getValue();
            boolean eligible = !player.isDead() && player.getHealth() > 0 && player.getHealth() < max
                    && player.getFoodLevel() >= rules.get().minimumFood()
                    && Boolean.TRUE.equals(player.getWorld().getGameRuleValue(GameRules.NATURAL_HEALTH_REGENERATION));
            if (!eligible) {
                regenerating.remove(player.getUniqueId());
                continue;
            }
            Long since = regenerating.putIfAbsent(player.getUniqueId(), now);
            if (since != null && now - since >= interval) {
                player.setHealth(Math.min(max, player.getHealth() + rules.get().amount()));
                player.setExhaustion(player.getExhaustion() + (float) rules.get().exhaustion());
                regenerating.put(player.getUniqueId(), now);
            }
        }
    }

    // ------------------------------------------------------------------
    // Golden apples
    // ------------------------------------------------------------------

    /**
     * A golden apple eaten the 1.7.10 way: the game's own eating is refused, and the
     * apple is used up here with its 1.7 food and effects.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onEat(PlayerItemConsumeEvent event) {
        Material type = event.getItem().getType();
        if (type != Material.GOLDEN_APPLE && type != Material.ENCHANTED_GOLDEN_APPLE) {
            return;
        }
        Optional<LegacyCombatSettings.GoldenApples> apples = classic().map(LegacyCombatSettings::goldenApples)
                .filter(LegacyCombatSettings.GoldenApples::enabled);
        if (apples.isEmpty()) {
            return;
        }
        Apple apple = type == Material.GOLDEN_APPLE ? apples.get().golden() : apples.get().enchanted();
        event.setCancelled(true);
        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand();
        ItemStack held = player.getInventory().getItem(hand);
        if (held.getType() != type) {
            return;
        }
        // The game's eating is cancelled: the item cooldown (pvp.yml) is started here.
        module.itemUsed(player, held);
        held.setAmount(held.getAmount() - 1);
        player.getInventory().setItem(hand, held.getAmount() <= 0 ? null : held);
        int food = Math.min(20, player.getFoodLevel() + apple.food());
        player.setFoodLevel(food);
        player.setSaturation((float) Math.min(food, player.getSaturation() + apple.saturation()));
        for (AppleEffect effect : apple.effects()) {
            PotionEffectType effectType = effectType(effect.effect());
            if (effectType != null) {
                player.addPotionEffect(new PotionEffect(effectType, effect.seconds() * 20, effect.level() - 1));
            }
        }
    }

    private PotionEffectType effectType(String key) {
        try {
            PotionEffectType type = Registry.MOB_EFFECT.get(NamespacedKey.minecraft(key));
            if (type == null) {
                module.getPlugin().getLogger().warning("pvp.yml: legacy-combat.golden-apples names an unknown"
                        + " effect '" + key + "'; left out.");
            }
            return type;
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Fishing rod
    // ------------------------------------------------------------------

    /**
     * A rod's hook hitting a player knocks them back, shows them hurt and tags both,
     * as in 1.7 - where the hook dealt a hit of no damage. Only a player the angler
     * could hit.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onHook(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof FishHook)
                || !(event.getEntity().getShooter() instanceof Player angler)
                || !(event.getHitEntity() instanceof Player victim)
                || angler.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        Optional<LegacyCombatSettings> settings = classic().filter(c -> c.fishingRod().enabled());
        if (settings.isEmpty() || module.judgeHarm(angler, victim).isPresent()) {
            return;
        }
        LegacyMath.Velocity current = velocity(victim.getVelocity());
        double towardsX = angler.getLocation().getX() - victim.getLocation().getX();
        double towardsZ = angler.getLocation().getZ() - victim.getLocation().getZ();
        LegacyMath.Velocity pushed = LegacyMath.knockback(current, towardsX, towardsZ,
                knockbackResistance(victim), settings.get().knockback());
        victim.setVelocity(vector(pushed));
        // Paper's hurt animation takes where the hit comes from relative to the victim's facing;
        // Minecraft's yaw of a direction (dx, dz) is atan2(-dx, dz).
        float fromYaw = (float) Math.toDegrees(Math.atan2(-towardsX, towardsZ));
        victim.playHurtAnimation(fromYaw - victim.getLocation().getYaw());
        module.tagForHit(angler, victim);
        if (settings.get().fishingRod().removeHook()) {
            // The game hooks the player it hit, and the bobber stays on them until reeled
            // in, pulling them - a PvP rod hits and comes back to be cast again. A tick
            // later: the game hooks the player right after this event.
            FishHook hook = (FishHook) event.getEntity();
            Bukkit.getScheduler().runTask(module.getPlugin(), () -> {
                if (hook.isValid()) {
                    hook.remove();
                }
            });
        }
    }

    /**
     * Reeling in a hooked player is refused: the bobber is taken back a tick after
     * the hit ({@link #onHook}), and a right-click inside that tick used to pull the
     * player - 26.2 {@code FishingHook#retrieve} fires this event, state
     * {@code CAUGHT_ENTITY}, before {@code pullEntity}, and stops there if cancelled.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onReel(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_ENTITY || !(event.getCaught() instanceof Player)
                || !classic().map(c -> c.fishingRod().enabled() && c.fishingRod().removeHook()).orElse(false)) {
            return;
        }
        event.setCancelled(true);
        event.getHook().remove();
    }

    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        regenerating.remove(event.getPlayer().getUniqueId());
        refusals.forget(event.getPlayer().getUniqueId());
        restoreRegeneration(event.getPlayer());
    }

    private void tell(Player player, String key) {
        if (refusals.tryTell(player.getUniqueId(), System.currentTimeMillis())) {
            module.getLang().send(player, key);
        }
    }
}
