package com.lawkeys.hcfcore.claim.listener;

import com.lawkeys.hcfcore.claim.ClaimManager;
import com.lawkeys.hcfcore.claim.ClaimMessages;
import com.lawkeys.hcfcore.claim.ClaimModule;
import com.lawkeys.hcfcore.claim.ProtectionResult;
import com.lawkeys.hcfcore.team.Team;
import com.lawkeys.hcfcore.util.ChunkPosition;
import com.lawkeys.hcfcore.util.RefusalThrottle;
import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.ChestBoat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Vehicle;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.projectiles.BlockProjectileSource;
import org.bukkit.tag.DamageTypeTags;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Enforces territory protection on the world-modifying events.
 *
 * <p>This listener holds no rules of its own: it resolves the chunk and asks
 * {@link ClaimManager#checkProtection}, which is where the reclaim logic lives.
 * That is what makes protection follow DTR automatically - nothing here knows
 * what a raid is.
 *
 * <p><strong>Hot path.</strong> These handlers run on every block event on the
 * server, so they return as early as possible and allocate nothing in the common
 * case: {@code ProtectionResult} is an enum and the owner lookup is a single
 * hash-map read.
 *
 * <p>Explosions are filtered here too, block by block rather than by cancelling
 * the blast: a charge going off on a border must take out the wilderness half and
 * leave the protected half standing.
 *
 * <p>So are the entities a base keeps outside its blocks - item frames, paintings,
 * armor stands, chest minecarts and boats (see {@link #isGuarded}). What the world
 * does on its own across a border - pistons, liquids, fire, dispensers - is
 * {@link CrossBorderListener}'s.
 */
public final class ClaimProtectionListener implements Listener {

    /** Lets staff build anywhere, for fixing griefs and building spawn. */
    public static final String BYPASS_PERMISSION = "hcfcore.claim.bypass";

    /** Used in the air without being consumed (names checked against Paper 26.2). */
    private static final Set<Material> USED_IN_THE_AIR = EnumSet.of(
            Material.SPLASH_POTION, Material.LINGERING_POTION, Material.ENDER_PEARL,
            Material.SNOWBALL, Material.EGG, Material.BLUE_EGG, Material.BROWN_EGG,
            Material.EXPERIENCE_BOTTLE, Material.WIND_CHARGE, Material.TRIDENT,
            Material.BOW, Material.CROSSBOW, Material.FISHING_ROD, Material.SHIELD);

    /**
     * How often a player is told that a click was refused. A held button repeats the
     * click five times a second; two seconds is one message for a long hold, and
     * still an answer to every separate try.
     */
    private static final long REFUSAL_MESSAGE_INTERVAL_MILLIS = 2000L;

    private final ClaimModule module;
    private final RefusalThrottle refusalMessages = new RefusalThrottle(REFUSAL_MESSAGE_INTERVAL_MILLIS);

    public ClaimProtectionListener(ClaimModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onBlockBreak(BlockBreakEvent event) {
        if (isBuildDenied(event.getPlayer(), event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isBuildDenied(event.getPlayer(), event.getBlock())) {
            event.setCancelled(true);
        }
    }

    /**
     * Buckets are judged on {@code getBlock()}, not {@code getBlockClicked()}.
     *
     * <p>The javadoc calls the former "the block involved in this event": for an
     * empty that is where the liquid lands, which is one block away from the one
     * that was clicked. Checking the clicked block let a player stand in the
     * wilderness, click the outward face of a claim's border block, and pour lava
     * into territory they cannot build in.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (isBuildDenied(event.getPlayer(), event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (isBuildDenied(event.getPlayer(), event.getBlock())) {
            event.setCancelled(true);
        }
    }

    /**
     * Refuses the clicked block inside protected territory: containers, doors and
     * switches, whatever the item in hand would do to it (a flint and steel, bone
     * meal, a block placed against it), a pressure plate or tripwire stepped on,
     * crops trampled. Clicks on air carry no block and are ignored.
     *
     * <p><strong>The block, never the item in hand.</strong> {@code setCancelled(true)}
     * refuses both, and its javadoc says what that costs: it "will prevent use of
     * food [...], prevent bows/snowballs/eggs from firing". Standing in a claim - or
     * at spawn - a player looking at the ground could neither eat nor throw a potion
     * (found in game, 14/09/2026): an attacker on enemy land could not heal while the
     * defenders could. Read in Paper's server sources at the 26.2 build: with only
     * the block denied, the server never applies the item to the block, and the use
     * that follows - eating, drinking, throwing - is refused only when the item was
     * denied too. Left clicks and stepping read {@code isCancelled()}, which is the
     * block's result, so breaking and plates are refused exactly as before.
     *
     * <p>The message is rationed ({@link #shouldExplain}, {@link RefusalThrottle});
     * the refusal never is.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        Player player = event.getPlayer();
        ProtectionResult refused = refusal(player, block, false);
        if (refused == null) {
            return;
        }
        event.setUseInteractedBlock(Event.Result.DENY);
        if (shouldExplain(event) && refusalMessages.tryTell(player.getUniqueId(), System.currentTimeMillis())) {
            explain(player, block, refused);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        refusalMessages.forget(event.getPlayer().getUniqueId());
    }

    /**
     * Whether a refused click is worth a message.
     *
     * <p>Not for stepping: the event fires every tick a player stands on a plate, so
     * each tick was a line in chat. Not for a right click while the player holds, in
     * either hand, something used in the air: the item still works, so "this is
     * protected" would be a lie. Either hand, because a click is judged once per hand
     * - a sword in the main hand must not answer for an apple eaten from the other.
     */
    private static boolean shouldExplain(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL) {
            return false;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return true;
        }
        PlayerInventory inventory = event.getPlayer().getInventory();
        return !isUsedInTheAir(inventory.getItemInMainHand()) && !isUsedInTheAir(inventory.getItemInOffHand());
    }

    /**
     * Eaten, drunk, thrown or fired: what a right click does with the item whatever
     * the player is looking at. Anything missing here only costs a needless message.
     */
    private static boolean isUsedInTheAir(ItemStack item) {
        return !item.isEmpty()
                && (item.hasData(DataComponentTypes.CONSUMABLE) || USED_IN_THE_AIR.contains(item.getType()));
    }

    /**
     * Putting an item in a frame or turning it, dressing or stripping an armor stand,
     * opening a chest minecart or a chest boat.
     *
     * <p>{@link PlayerInteractAtEntityEvent} and the armor stand's own event are
     * subclasses of this one; the first declares no handler list and reaches this
     * handler, and cancelling it stops the second from firing at all (26.2 sources).
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (isGuarded(entity) && isEntityDenied(event.getPlayer(), entity)) {
            event.setCancelled(true);
        }
    }

    /**
     * Knocking the item out of a frame or breaking an armor stand, by hand, by arrow
     * or by blast. Paper reports both as damage to the entity before anything drops
     * (26.2 sources); a frame's item lost to damage is the only way to take it.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onEntityDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if ((entity instanceof ItemFrame || entity instanceof ArmorStand)
                && mustSurvive(entity, event instanceof EntityDamageByEntityEvent byEntity ? byEntity.getDamager() : null,
                isExplosion(event.getDamageSource()))) {
            event.setCancelled(true);
        }
    }

    /** Breaking an empty frame or a painting; {@link HangingBreakByEntityEvent} has no list of its own. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onHangingBreak(HangingBreakEvent event) {
        boolean explosion = event.getCause() == HangingBreakEvent.RemoveCause.EXPLOSION;
        Entity remover = event instanceof HangingBreakByEntityEvent byEntity ? byEntity.getRemover() : null;
        // Anything else - the wall behind it gone - follows from a break judged already.
        if ((explosion || remover != null) && mustSurvive(event.getEntity(), remover, explosion)) {
            event.setCancelled(true);
        }
    }

    /**
     * Hanging a frame or a painting: judged where it hangs, which is one block off
     * the block clicked - a player in the wilderness clicking the outer face of a
     * border block would otherwise hang it inside.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onHangingPlace(HangingPlaceEvent event) {
        Player player = event.getPlayer();
        if (player != null && isBuildDenied(player, event.getBlock().getRelative(event.getBlockFace()))) {
            event.setCancelled(true);
        }
    }

    /** Breaking a chest minecart or a chest boat, which spills what it holds. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onVehicleDamage(VehicleDamageEvent event) {
        Vehicle vehicle = event.getVehicle();
        if (isGuarded(vehicle) && mustSurvive(vehicle, event.getAttacker(), isExplosion(event.getDamageSource()))) {
            event.setCancelled(true);
        }
    }

    /**
     * What a base keeps outside its blocks: item frames, paintings, armor stands and
     * storage vehicles. They are judged like the block they stand in - a frame or an
     * armor stand is decoration, and a chest minecart is a chest on wheels - so
     * nobody takes, strips or empties them where they could not break a block.
     */
    private static boolean isGuarded(Entity entity) {
        return entity instanceof Hanging || entity instanceof ArmorStand || entity instanceof ChestBoat
                || entity instanceof StorageMinecart || entity instanceof HopperMinecart;
    }

    /**
     * @param breaker   what is breaking it, or {@code null} when nothing is named
     * @param explosion whether it is a blast, which has no actor and is judged as
     *                  explosions are on blocks
     * @return whether the entity must be kept whole
     */
    private boolean mustSurvive(Entity entity, Entity breaker, boolean explosion) {
        ClaimManager claims = module.getManager();
        if (claims == null) {
            return false;
        }
        Location at = entity.getLocation();
        if (explosion) {
            return claims.isExplosionProtected(at.getWorld().getName(), at.getBlockX(), at.getBlockY(), at.getBlockZ());
        }
        if (breaker instanceof Projectile projectile) {
            if (projectile.getShooter() instanceof BlockProjectileSource source) {
                Block dispenser = source.getBlock();
                return !claims.mayReach(at.getWorld().getName(), dispenser.getX(), dispenser.getZ(),
                        at.getBlockX(), at.getBlockY(), at.getBlockZ());
            }
            breaker = projectile.getShooter() instanceof Player shooter ? shooter : null;
        }
        return breaker instanceof Player player && isEntityDenied(player, entity);
    }

    /** Like {@link #isBuildDenied}, with the message rationed: a held button repeats. */
    private boolean isEntityDenied(Player player, Entity entity) {
        Block block = entity.getLocation().getBlock();
        ProtectionResult refused = refusal(player, block, true);
        if (refused == null) {
            return false;
        }
        if (refusalMessages.tryTell(player.getUniqueId(), System.currentTimeMillis())) {
            explain(player, block, refused);
        }
        return true;
    }

    private static boolean isExplosion(DamageSource source) {
        return DamageTypeTags.IS_EXPLOSION.isTagged(source.getDamageType());
    }

    /**
     * Keeps explosions off protected territory - TNT, creepers, end crystals.
     *
     * <p>Without this, every other rule in this class is optional: a player
     * refused a block break throws a stick of TNT instead.
     *
     * <p>The blast itself is never cancelled, only trimmed. Cancelling would stop
     * an explosion straddling a border from touching the unprotected side, which
     * is not what protection means.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onEntityExplode(EntityExplodeEvent event) {
        protect(event.blockList());
    }

    /** The other half: beds, respawn anchors and anything else the server explodes. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onBlockExplode(BlockExplodeEvent event) {
        protect(event.blockList());
    }

    /**
     * Drops the protected blocks from a blast list.
     *
     * <p>Block by block, not chunk by chunk: inside the warzone, the blocks of a
     * region another system governs - a Mountain - follow that system's rules, so
     * two blocks of one chunk can get different answers. A blast is a few hundred
     * blocks at most, and each answer a couple of hash lookups.
     */
    private void protect(List<Block> blocks) {
        ClaimManager claims = module.getManager();
        if (claims == null || blocks.isEmpty()) {
            return;
        }
        blocks.removeIf(block -> claims.isExplosionProtected(block.getWorld().getName(),
                block.getX(), block.getY(), block.getZ()));
    }

    /**
     * Announces territory borders.
     *
     * <p>{@link PlayerMoveEvent} fires many times per second per player, so this
     * exits on the first comparison unless the player actually changed chunk.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        if (!module.getSettings().protection().announceTerritory()) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (ChunkPosition.toChunk(from.getBlockX()) == ChunkPosition.toChunk(to.getBlockX())
                && ChunkPosition.toChunk(from.getBlockZ()) == ChunkPosition.toChunk(to.getBlockZ())
                && Objects.equals(from.getWorld(), to.getWorld())) {
            return;
        }

        ClaimManager claims = module.getManager();
        if (claims == null) {
            return;
        }
        ChunkPosition fromChunk = ClaimModule.toChunk(from);
        ChunkPosition toChunk = ClaimModule.toChunk(to);
        if (claims.isSameTerritory(fromChunk, toChunk)) {
            return;
        }

        Player player = event.getPlayer();
        Optional<String> current = territoryName(claims, toChunk);
        if (current.isPresent()) {
            module.getLang().send(player, ClaimMessages.ENTER_TERRITORY, "team", current.get());
        } else {
            module.getLang().send(player, ClaimMessages.LEAVE_TERRITORY,
                    "team", territoryName(claims, fromChunk).orElse(""));
        }
    }

    /**
     * @return what a player is standing in, as border announcements name it: the
     *         owning team, the warzone, or nothing for wilderness
     */
    private Optional<String> territoryName(ClaimManager claims, ChunkPosition chunk) {
        Optional<Team> owner = claims.getOwner(chunk);
        if (owner.isPresent()) {
            return Optional.of(owner.get().getName());
        }
        return claims.isWarzone(chunk)
                ? Optional.of(module.getSettings().warzone().displayName())
                : Optional.empty();
    }

    /**
     * Placing, breaking and buckets: judged block by block, which is where the
     * warzone applies to unclaimed land (see {@code ClaimManager#checkBuild}).
     *
     * @return {@code true} if the action must be cancelled; the player is told why,
     *         since a silently cancelled event reads as a broken server - at most
     *         once per interval, since a held button breaks or places again and again
     */
    private boolean isBuildDenied(Player player, Block block) {
        ProtectionResult refused = refusal(player, block, true);
        if (refused == null) {
            return false;
        }
        if (refusalMessages.tryTell(player.getUniqueId(), System.currentTimeMillis())) {
            explain(player, block, refused);
        }
        return true;
    }

    /**
     * @param building whether this is building (place, break, bucket), which the
     *                 warzone restricts, rather than an interaction, which it does not
     * @return why the action is refused, or {@code null} if it is allowed
     */
    private ProtectionResult refusal(Player player, Block block, boolean building) {
        ClaimManager claims = module.getManager();
        if (claims == null) {
            // The claim module never started. This is not "still loading": the
            // manager exists before its load begins, and no player can join
            // until every load has landed (StartupGate).
            return null;
        }
        // The permission AND the staff build toggle (see ClaimModule#bypassesProtection):
        // holding the permission alone no longer builds through protection.
        if (module.bypassesProtection(player)) {
            return null;
        }

        String world = block.getWorld().getName();
        Team actorTeam = module.getTeams().getManager().getTeamOf(player.getUniqueId()).orElse(null);
        ProtectionResult result = building
                ? claims.checkBuild(actorTeam, world, block.getX(), block.getY(), block.getZ())
                : claims.checkProtection(actorTeam, ChunkPosition.fromBlock(world, block.getX(), block.getZ()));
        return result.isAllowed() ? null : result;
    }

    private void explain(Player player, Block block, ProtectionResult refused) {
        ChunkPosition chunk = ChunkPosition.fromBlock(block.getWorld().getName(), block.getX(), block.getZ());
        String ownerName = module.getManager().getOwner(chunk).map(Team::getName).orElse("");
        module.getLang().send(player, refused.getMessageKey(),
                "team", ownerName, "warzone", module.getSettings().warzone().displayName());
    }
}
