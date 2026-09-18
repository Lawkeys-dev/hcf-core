package com.lawkeys.hcfcore.claim.listener;

import com.lawkeys.hcfcore.claim.ClaimManager;
import com.lawkeys.hcfcore.claim.ClaimModule;
import io.papermc.paper.event.block.BlockPreDispenseEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.SpongeAbsorbEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.projectiles.BlockProjectileSource;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Keeps what the world does on its own from crossing into territory it may not
 * change: pistons, flowing liquids, spreading fire, dispensers, growing trees and
 * sponges.
 *
 * <p>None of these carries a player, so {@link ClaimProtectionListener} never saw
 * them: a sticky piston in the wilderness pulled a protected team's wall down, lava
 * poured one block outside flowed in, fire lit outside spread into a wooden base.
 * The rule is the project owner's, 15/09/2026, and lives in
 * {@link ClaimManager#mayReach}: what comes from another territory is judged as if
 * its owner did it.
 *
 * <p><strong>Hot path.</strong> Liquids and fire fire these constantly. The common
 * case - both blocks in one chunk - is answered with two shifts.
 */
public final class CrossBorderListener implements Listener {

    /**
     * What a dispenser fires rather than uses. An arrow or a potion flies off the
     * way a player would throw it, which the rule allows across a border - and the
     * fire a fire charge starts where it lands is judged by {@link #onIgnite}.
     * Everything else acts on the block in front of the dispenser (a bucket, a flint
     * and steel, bone meal, a shulker box, a spawn egg) or is dropped there. Names
     * checked against Paper 26.2.
     */
    private static final Set<Material> FIRED = EnumSet.of(
            Material.ARROW, Material.SPECTRAL_ARROW, Material.TIPPED_ARROW,
            Material.SNOWBALL, Material.EGG, Material.BLUE_EGG, Material.BROWN_EGG,
            Material.SPLASH_POTION, Material.LINGERING_POTION, Material.EXPERIENCE_BOTTLE,
            Material.FIRE_CHARGE, Material.FIREWORK_ROCKET, Material.WIND_CHARGE);

    private final ClaimModule module;

    public CrossBorderListener(ClaimModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        Block piston = event.getBlock();
        BlockFace direction = event.getDirection();
        if (!mayReach(piston, piston.getRelative(direction)) // the head itself
                || !mayMove(piston, event.getBlocks(), direction)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (!mayMove(event.getBlock(), event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    /**
     * Every moved block leaves where it is and lands one step on.
     *
     * <p>The event's direction is the way the blocks travel, a sticky piston's pull
     * included: Paper builds both events from the push direction, which is the
     * opposite of the piston's facing on a retraction (read in the 26.2 server
     * sources). The blocks a push breaks are in the same list; checking one step past
     * them too can only refuse, and only at a border.
     */
    private boolean mayMove(Block piston, List<Block> blocks, BlockFace direction) {
        for (Block block : blocks) {
            if (!mayReach(piston, block) || !mayReach(piston, block.getRelative(direction))) {
                return false;
            }
        }
        return true;
    }

    /** Liquids, and a dragon egg teleporting, which the same event reports. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onFlow(BlockFromToEvent event) {
        if (!mayReach(event.getBlock(), event.getToBlock())) {
            event.setCancelled(true);
        }
    }

    /**
     * Fire lit from another block - spreading fire, lava - or by a fire charge a
     * dispenser shot.
     *
     * <p>Fire a player starts is not judged here: a flint and steel on protected
     * land never reaches the block ({@link ClaimProtectionListener#onInteract}), and
     * a burning arrow sets no ordinary block alight (the Minecraft Wiki lists TNT,
     * campfires and candles). Lightning and mobs are the world's own doing.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onIgnite(BlockIgniteEvent event) {
        Block origin = event.getIgnitingBlock();
        if (origin == null && event.getIgnitingEntity() instanceof Projectile projectile
                && projectile.getShooter() instanceof BlockProjectileSource source) {
            origin = source.getBlock();
        }
        if (origin != null && !mayReach(origin, event.getBlock())) {
            event.setCancelled(true);
        }
    }

    /** A fire outside burning away - or into - a block inside. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onBurn(BlockBurnEvent event) {
        Block fire = event.getIgnitingBlock();
        if (fire != null && !mayReach(fire, event.getBlock())) {
            event.setCancelled(true);
        }
    }

    /** Fire again - Paper fires this after the ignition - and vines, grass, sculk. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onSpread(BlockSpreadEvent event) {
        if (!mayReach(event.getSource(), event.getBlock())) {
            event.setCancelled(true);
        }
    }

    /**
     * A dispenser acting on the block in front of it.
     *
     * <p>Paper's pre-dispense event, fired once for every item a dispenser is about
     * to use, before whatever the item does (read in the 26.2 server sources): one
     * handler covers buckets, fire, bone meal and everything else, where the
     * per-behaviour dispense event would have to be trusted to fire for each. A
     * dropper only drops items or passes them on, which anybody may do across a
     * border.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onDispense(BlockPreDispenseEvent event) {
        Block dispenser = event.getBlock();
        if (dispenser.getType() != Material.DISPENSER || FIRED.contains(event.getItemStack().getType())) {
            return;
        }
        if (dispenser.getBlockData() instanceof Directional directional
                && !mayReach(dispenser, dispenser.getRelative(directional.getFacing()))) {
            event.setCancelled(true);
        }
    }

    /**
     * A tree or a huge mushroom growing into another territory does not grow at
     * all: whether the event's block list may be trimmed is not documented, and
     * cancelling is.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onGrow(StructureGrowEvent event) {
        Location origin = event.getLocation();
        for (BlockState part : event.getBlocks()) {
            if (!mayReach(origin.getWorld().getName(), origin.getBlockX(), origin.getBlockZ(), part)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /** A sponge draining another territory's water; its javadoc says the list is mutable. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onAbsorb(SpongeAbsorbEvent event) {
        Block sponge = event.getBlock();
        event.getBlocks().removeIf(water ->
                !mayReach(sponge.getWorld().getName(), sponge.getX(), sponge.getZ(), water));
    }

    private boolean mayReach(Block from, Block to) {
        ClaimManager claims = module.getManager();
        return claims == null || claims.mayReach(from.getWorld().getName(),
                from.getX(), from.getZ(), to.getX(), to.getY(), to.getZ());
    }

    private boolean mayReach(String world, int fromX, int fromZ, BlockState to) {
        ClaimManager claims = module.getManager();
        return claims == null || claims.mayReach(world, fromX, fromZ, to.getX(), to.getY(), to.getZ());
    }
}
