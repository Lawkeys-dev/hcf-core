package com.lawkeys.hcfcore.ability.listener;

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import com.lawkeys.hcfcore.ability.Ability;
import com.lawkeys.hcfcore.ability.AbilityMenu;
import com.lawkeys.hcfcore.ability.AbilityMessages;
import com.lawkeys.hcfcore.ability.AbilityModule;
import com.lawkeys.hcfcore.ability.AbilityType;
import com.lawkeys.hcfcore.ability.PocketBardMenu;
import com.lawkeys.hcfcore.ability.PocketBardItem;
import com.lawkeys.hcfcore.util.Durations;
import com.lawkeys.hcfcore.util.RefusalThrottle;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.PotionContents;
import org.bukkit.block.Block;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerEggThrowEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionType;

import java.util.Objects;
import java.util.Optional;

/**
 * Where abilities are used - a click, a throw, a hit, a shot - and where what they
 * leave behind holds: anti-build, Berserk's potions, the menus. Every rule is the
 * module's; this class only finds the event.
 */
public final class AbilityListener implements Listener {

    private static final long MESSAGE_EVERY_MILLIS = 1_500L;

    private final AbilityModule module;
    private final RefusalThrottle refusals = new RefusalThrottle(MESSAGE_EVERY_MILLIS);

    public AbilityListener(AbilityModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    // ------------------------------------------------------------------
    // Use
    // ------------------------------------------------------------------

    /**
     * A right-click. At {@code LOW}: after a freeze refuses the click at
     * {@code LOWEST}, before territory protection. Not {@code ignoreCancelled}: a
     * click in the air arrives already cancelled, and it is how most abilities are
     * used - what is read instead is whether somebody refused the item.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.useItemInHand() == Event.Result.DENY) {
            return;
        }
        ItemStack item = event.getItem();
        Optional<PocketBardItem> pocket = module.pocketItemOf(item);
        if (pocket.isPresent()) {
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND) {
                module.usePocketItem(event.getPlayer(), pocket.get(), item);
            }
            return;
        }
        Optional<Ability> ability = module.abilityOf(item);
        if (ability.isEmpty()) {
            return;
        }
        switch (ability.get().type().trigger()) {
            case RIGHT_CLICK -> {
                // Never also placing the item or opening a chest.
                event.setCancelled(true);
                if (event.getHand() == EquipmentSlot.HAND) {
                    module.rightClick(event.getPlayer(), ability.get(), item);
                }
            }
            // Used by hitting with it: its click places nothing - a crafting table stays in hand.
            case HIT -> event.setCancelled(true);
            // Thrown, drawn or cast as usual; the launch, the shot and the reel are where it is used.
            case THROW, SHOOT, FISH -> {
            }
        }
    }

    /** An item that counts its uses is worn by them alone, never by a hit or a shot. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onWear(PlayerItemDamageEvent event) {
        if (module.wearsByUses(event.getItem())) {
            event.setCancelled(true);
        }
    }

    /** No ability item is ever placed, whatever else refuses or allows it. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlace(BlockPlaceEvent event) {
        if (module.isPartnerItem(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onLaunch(PlayerLaunchProjectileEvent event) {
        Player player = event.getPlayer();
        Projectile projectile = event.getProjectile();
        Optional<Ability> thrown = module.abilityOf(event.getItemStack())
                .filter(a -> a.type().trigger() == AbilityType.Trigger.THROW);
        if (projectile instanceof EnderPearl && thrown.isEmpty()) {
            module.recordPearl(player);
            return;
        }
        if (projectile instanceof ThrownPotion && refusePotion(player, event.getItemStack())) {
            event.setShouldConsume(false);
            event.setCancelled(true);
            return;
        }
        if (thrown.isPresent() && !module.launch(player, thrown.get(), projectile)) {
            event.setShouldConsume(false);
            event.setCancelled(true);
        }
    }

    /** A thrown ability egg hatches no chicken. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEgg(PlayerEggThrowEvent event) {
        if (module.thrownAbility(event.getEgg()).isPresent()) {
            event.setHatching(false);
        }
    }

    /** A fake pearl comes down: gone before it can teleport anybody. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onFakePearl(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (projectile instanceof EnderPearl && module.isFakePearl(projectile)) {
            event.setCancelled(true);
            projectile.getWorld().spawnParticle(org.bukkit.Particle.PORTAL, projectile.getLocation(), 20);
            projectile.remove();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLand(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        module.thrownAbility(projectile)
                .filter(a -> a.type().trigger() == AbilityType.Trigger.THROW || a.type() == AbilityType.SHOTGUN)
                .ifPresent(ability -> module.landed(projectile, ability,
                        event.getHitEntity() instanceof Player hit ? hit : null));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player) || !(event.getProjectile() instanceof Projectile arrow)) {
            return;
        }
        Optional<Ability> ability = module.abilityOf(event.getBow())
                .filter(a -> a.type() == AbilityType.PORTABLE_ARCHER);
        if (ability.isPresent() && !module.shoot(player, ability.get(), arrow, event.getBow())) {
            event.setCancelled(true);
            player.updateInventory();
        }
    }

    /** A Grappling Hook reeled in while its hook is stuck in a block, lies on one, or touches one's side. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onReel(PlayerFishEvent event) {
        FishHook hook = event.getHook();
        boolean stuck = event.getState() == PlayerFishEvent.State.IN_GROUND
                || (event.getState() == PlayerFishEvent.State.REEL_IN && AbilityModule.hookHeld(hook));
        if (!stuck || event.getHand() == null) {
            return;
        }
        Player player = event.getPlayer();
        module.abilityOf(player.getInventory().getItem(event.getHand()))
                .filter(a -> a.type() == AbilityType.GRAPPLING_HOOK)
                .ifPresent(ability -> module.grapple(player, ability, hook));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && event.getEntity() instanceof Player player
                && module.cancelsFall(player)) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------
    // Hits
    // ------------------------------------------------------------------

    /** The Sun's fireworks only show. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onFirework(EntityDamageByEntityEvent event) {
        if (module.isAbilityFirework(event.getDamager())) {
            event.setCancelled(true);
        }
    }

    /**
     * A blow between two players, not refused: the abilities running change it -
     * Lucky Mode, Focus Mode - and the hit-with ones count it. At {@code HIGHEST},
     * after the combat rules and Strength ({@code HIGH}), with the archer tag: they
     * multiply the same hit.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBlow(EntityDamageByEntityEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || !(event.getDamager() instanceof Player attacker)
                || !(event.getEntity() instanceof Player victim) || attacker.equals(victim)) {
            return;
        }
        double multiplier = module.meleeHit(attacker, victim);
        if (multiplier != 1.0) {
            event.setDamage(event.getDamage() * multiplier);
        }
    }

    /** A hit that landed: who hit whom, a Portable Archer's tag, an invisibility undone. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onHitLanded(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = null;
        if (event.getDamager() instanceof Player direct) {
            attacker = direct;
        } else if (event.getDamager() instanceof Projectile shot && shot.getShooter() instanceof Player shooter) {
            attacker = shooter;
            Player tagger = shooter;
            module.thrownAbility(shot)
                    .filter(a -> a.type() == AbilityType.PORTABLE_ARCHER)
                    .ifPresent(ability -> {
                        if (!tagger.equals(victim)) {
                            module.archerTag(tagger, victim, ability);
                        }
                    });
        }
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        module.recordHit(attacker, victim);
        module.hitWhileInvisible(victim);
        module.reflect(attacker, victim, event.getFinalDamage());
    }

    // ------------------------------------------------------------------
    // Anti-build
    // ------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBuild(BlockPlaceEvent event) {
        if (refuseAntiBuild(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBreak(BlockBreakEvent event) {
        if (refuseAntiBuild(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBucket(PlayerBucketEmptyEvent event) {
        if (refuseAntiBuild(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (refuseAntiBuild(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /** Opening a chest, a gate, a trapdoor: refusing the block, never the item in hand. */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onOpen(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null
                || !module.blockedUnderAntiBuild(block.getType())) {
            return;
        }
        if (refuseAntiBuild(event.getPlayer())) {
            event.setUseInteractedBlock(Event.Result.DENY);
        }
    }

    private boolean refuseAntiBuild(Player player) {
        long left = module.antiBuildLeft(player);
        if (left <= 0) {
            return false;
        }
        if (refusals.tryTell(player.getUniqueId(), System.currentTimeMillis())) {
            module.getLang().send(player, AbilityMessages.ANTI_BUILD_REFUSED,
                    "time", Durations.formatWithSeconds(Durations.secondsLeft(left)));
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Berserk: no potions
    // ------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onDrink(PlayerItemConsumeEvent event) {
        if (refusePotion(event.getPlayer(), event.getItem())) {
            event.setCancelled(true);
        }
    }

    private boolean refusePotion(Player player, ItemStack item) {
        if (item == null) {
            return false;
        }
        PotionContents contents = item.getData(DataComponentTypes.POTION_CONTENTS);
        PotionType type = contents == null ? null : contents.potion();
        if (type == null) {
            return false;
        }
        long left = module.berserkRefuses(player, type.getKey().getKey());
        if (left <= 0) {
            return false;
        }
        if (refusals.tryTell(player.getUniqueId(), System.currentTimeMillis())) {
            module.getLang().send(player, AbilityMessages.NO_POTIONS,
                    "time", Durations.formatWithSeconds(Durations.secondsLeft(left)));
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Menus
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onMenuClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder(false);
        if (holder instanceof AbilityMenu) {
            event.setCancelled(true);
            return;
        }
        if (!(holder instanceof PocketBardMenu menu)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getInventory() || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        menu.at(event.getSlot()).ifPresent(picked -> module.pickPocketBard(player, menu.pocketBard(), picked));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMenuDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder(false);
        if (holder instanceof AbilityMenu || holder instanceof PocketBardMenu) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------

    /** A helmet lent to a pumpkin drops as itself. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPumpkinDeath(PlayerDeathEvent event) {
        module.pumpkinDeath(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        module.forget(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        module.forgetAll(event.getPlayer());
        refusals.forget(event.getPlayer().getUniqueId());
    }
}
