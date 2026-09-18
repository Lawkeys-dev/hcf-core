package com.lawkeys.hcfcore.resourcenode.listener;

import com.lawkeys.hcfcore.resourcenode.ResourceNodeDefinition;
import com.lawkeys.hcfcore.resourcenode.ResourceNodeManager;
import com.lawkeys.hcfcore.resourcenode.ResourceNodeMessages;
import com.lawkeys.hcfcore.resourcenode.ResourceNodeModule;
import com.lawkeys.hcfcore.util.RefusalThrottle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Keeps a resource node's region minable but not buildable.
 *
 * <p>FEATURES.md section 6 wants a mountain that players can only mine: no
 * building, no claiming, and - with the default break policy - nothing to take
 * but the resource itself, so the structure survives to be refilled.
 *
 * <p><strong>Hot path.</strong> These handlers run on every block event on the
 * server. The common case is a block outside every region, which costs one
 * comparison per configured node and allocates nothing.
 *
 * <p>Explosions are filtered block by block rather than by cancelling the blast,
 * the same way {@code claim/} does it: a charge going off at the foot of the
 * mountain must still take out the ground around it.
 */
public final class ResourceNodeProtectionListener implements Listener {

    /** Lets staff build inside a node's region, for shaping the mountain itself. */
    public static final String BYPASS_PERMISSION = "hcfcore.resourcenode.bypass";

    /** At most one refusal message every two seconds, as in claim/: mining repeats. */
    private static final long REFUSAL_MESSAGE_INTERVAL_MILLIS = 2000L;

    private final ResourceNodeModule module;
    private final RefusalThrottle refusalMessages = new RefusalThrottle(REFUSAL_MESSAGE_INTERVAL_MILLIS);

    public ResourceNodeProtectionListener(ResourceNodeModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (deniesBuilding(event.getPlayer(), event.getBlock())) {
            event.setCancelled(true);
        }
    }

    /**
     * Emptying a bucket is building - with lava, it is also how a region gets ruined.
     *
     * <p>{@code getBlock()}, not {@code getBlockClicked()}: the javadoc calls the
     * former "the block involved in this event", which for an empty is where the
     * liquid ends up. Judging the clicked block instead would let a player stand
     * one block outside the region, click its outer face, and pour lava in.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (deniesBuilding(event.getPlayer(), event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onBlockBreak(BlockBreakEvent event) {
        if (deniesBreaking(event.getPlayer(), event.getBlock())) {
            event.setCancelled(true);
        }
    }

    /**
     * Filling a bucket removes the liquid, so it goes through the break rule - on
     * the block the liquid is taken from, for the same reason as above.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (deniesBreaking(event.getPlayer(), event.getBlock())) {
            event.setCancelled(true);
        }
    }

    /**
     * Keeps explosions out of the region.
     *
     * <p>Without this the break policy is decorative: a player refused the
     * netherrack simply blows it up, and after a few days there is no mountain
     * left for the refill to fill.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onEntityExplode(EntityExplodeEvent event) {
        protect(event.blockList());
    }

    /** The other half: beds, respawn anchors - which is to say, the Nether. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onBlockExplode(BlockExplodeEvent event) {
        protect(event.blockList());
    }

    /**
     * Keeps pistons from carrying blocks across a region's edge.
     *
     * <p>A piston outside the region could push blocks in to wall the resource off
     * - building, by machine - or pull the structure out, taking what the break
     * policy protects. Every moved block is checked together with its neighbours on
     * both sides of the piston's axis, so the answer does not depend on which way
     * "the direction in which the piston will operate" points for a retraction; the
     * javadoc does not spell that out, and at the region's edge this errs towards
     * refusing.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (pistonTouchesRegion(event.getBlock(), event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (pistonTouchesRegion(event.getBlock(), event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    /**
     * Keeps liquids from flowing into a region from outside.
     *
     * <p>Lava poured against the mountain's foot would otherwise flow in and burn
     * whoever is mining: building by proxy, one block outside where the bucket rule
     * can see it. Liquid already inside - part of the design - keeps flowing.
     *
     * <p>Every flowing liquid on the server fires this. The common case, a block
     * outside every region, costs one comparison per configured node.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onLiquidFlow(BlockFromToEvent event) {
        if (isBuildProtected(event.getToBlock()) && !isBuildProtected(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    private boolean pistonTouchesRegion(Block piston, List<Block> moved, BlockFace direction) {
        if (isBuildProtected(piston.getRelative(direction))) {
            return true; // the head itself reaching into the region
        }
        BlockFace opposite = direction.getOppositeFace();
        for (Block block : moved) {
            if (isBuildProtected(block) || isBuildProtected(block.getRelative(direction))
                    || isBuildProtected(block.getRelative(opposite))) {
                return true;
            }
        }
        return false;
    }

    private boolean isBuildProtected(Block block) {
        ResourceNodeManager manager = module.getManager();
        return manager != null && manager.isBuildProtected(block.getWorld().getName(),
                block.getX(), block.getY(), block.getZ());
    }

    /** Drops the region's blocks from a blast list. */
    private void protect(List<Block> blocks) {
        ResourceNodeManager manager = module.getManager();
        if (manager == null || blocks.isEmpty()) {
            return;
        }
        // No per-chunk memo here, unlike claim/: a region is a box, so the answer
        // genuinely differs from one block to the next inside the same chunk.
        blocks.removeIf(block -> manager.isExplosionProtected(block.getWorld().getName(),
                block.getX(), block.getY(), block.getZ()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        refusalMessages.forget(event.getPlayer().getUniqueId());
    }

    private boolean deniesBuilding(Player player, Block block) {
        Optional<ResourceNodeDefinition> node = nodeAt(player, block);
        if (node.isEmpty() || !node.get().preventBuild()) {
            return false;
        }
        if (refusalMessages.tryTell(player.getUniqueId(), System.currentTimeMillis())) {
            module.getLang().send(player, ResourceNodeMessages.NO_BUILD, "node", node.get().displayName());
        }
        return true;
    }

    private boolean deniesBreaking(Player player, Block block) {
        Optional<ResourceNodeDefinition> node = nodeAt(player, block);
        if (node.isEmpty() || node.get().allowsBreaking(block.getType().name())) {
            return false;
        }
        if (refusalMessages.tryTell(player.getUniqueId(), System.currentTimeMillis())) {
            module.getLang().send(player, ResourceNodeMessages.NO_BREAK, "node", node.get().displayName());
        }
        return true;
    }

    /**
     * @return the node whose region holds that block, or empty when there is none
     *         or the player may ignore it
     */
    private Optional<ResourceNodeDefinition> nodeAt(Player player, Block block) {
        ResourceNodeManager manager = module.getManager();
        if (manager == null || player.hasPermission(BYPASS_PERMISSION)) {
            return Optional.empty();
        }
        return manager.nodeAtBlock(block.getWorld().getName(),
                block.getX(), block.getY(), block.getZ());
    }
}
