package com.lawkeys.hcfcore.limiter.listener;

import com.lawkeys.hcfcore.claim.ClaimModule;
import com.lawkeys.hcfcore.limiter.LimiterModule;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkLoadEvent;

import java.util.List;
import java.util.Objects;

/**
 * Blocks per claim, where the world changes: a placement refused at the limit, and
 * the counts kept up as limited blocks come and go.
 *
 * <p>The refusal is {@code HIGH} and skips cancelled placements, so claim protection
 * decides first whether the block could be placed at all; the counting is
 * {@code MONITOR}, for what really happened. A piston's moved blocks are not followed
 * one by one - where a retraction puts them is not something to guess - their chunks
 * are counted again a few ticks later, once the move is done.
 * {@code BlockMultiPlaceEvent} (beds, doors) shares {@code BlockPlaceEvent}'s handler
 * list, so it is seen here too; a two-block door counts one at placement and is put
 * right by the next recount.
 */
public final class ClaimBlockListener implements Listener {

    private final LimiterModule module;

    public ClaimBlockListener(LimiterModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        module.refusal(event.getPlayer(), event.getBlockPlaced()).ifPresent(refusal -> {
            event.setCancelled(true);
            module.getLang().send(event.getPlayer(), "limiter.claim-blocks.refused",
                    "block", LimiterModule.display(refusal.block()),
                    "count", String.valueOf(refusal.count()), "limit", String.valueOf(refusal.limit()));
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlaced(BlockPlaceEvent event) {
        Block placed = event.getBlockPlaced();
        module.changed(placed, placed.getType(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        module.changed(event.getBlock(), event.getBlock().getType(), -1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        gone(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        gone(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        module.changed(event.getBlock(), event.getBlock().getType(), -1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        moved(event.getBlocks(), event.getDirection());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        moved(event.getBlocks(), event.getDirection());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        module.chunkLoaded(event.getChunk());
    }

    private void gone(List<Block> blocks) {
        for (Block block : blocks) {
            module.changed(block, block.getType(), -1);
        }
    }

    /** The chunk each limited block leaves, and the ones it may enter on either side. */
    private void moved(List<Block> blocks, BlockFace direction) {
        for (Block block : blocks) {
            if (!module.isLimited(block.getType())) {
                continue;
            }
            module.recountSoon(ClaimModule.toChunk(block.getLocation()));
            module.recountSoon(ClaimModule.toChunk(block.getRelative(direction).getLocation()));
            module.recountSoon(ClaimModule.toChunk(block.getRelative(direction.getOppositeFace()).getLocation()));
        }
    }
}
