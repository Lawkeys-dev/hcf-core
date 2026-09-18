package com.lawkeys.hcfcore.resourcenode;

import com.lawkeys.hcfcore.util.Cuboid;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * One refill in progress: the server-side half of a resource node.
 *
 * <p>This is the only class in the module that touches the world. It holds no
 * rule of its own - what to place and what may be overwritten were both decided
 * by the definition; it walks the region and applies that decision, a bounded
 * batch per tick.
 *
 * <p><strong>Why batches.</strong> A mountain is tens of thousands of blocks.
 * Setting them all in one tick would freeze the server for as long as it takes,
 * which on a PvP server is the difference between a fight and a complaint. The
 * walk lives in {@link RefillCursor}, which is testable without a server; what is
 * left here is the part that genuinely needs one.
 *
 * <p><strong>Chunk loading.</strong> {@code World#getBlockAt} loads a chunk that
 * is not loaded - synchronously, from disk, on the main thread - and a mountain
 * nobody is standing near is usually unloaded. That is the blocking I/O CONTRIBUTING.md
 * section 5 rules out. So a job starts out waiting: the server layer requests the
 * region's chunks asynchronously, holds them with a plugin ticket once they
 * arrive, and only then marks the job {@linkplain #markReady() ready} to walk.
 */
final class RefillJob {

    private final ResourceNodeDefinition node;
    private final World world;
    private final Map<String, Material> materials;
    private final boolean applyPhysics;
    private final RefillCursor cursor;

    private long placed;
    private long leftForPlayers;
    private boolean ready;
    private NodeUpdate announcement;

    /**
     * @param region the box to walk, which is the node's region already cut down to
     *               the world's own height range - a mountain configured taller
     *               than its world must not spend a refill on positions that cannot
     *               hold a block
     */
    RefillJob(ResourceNodeDefinition node, World world, Cuboid region,
              Map<String, Material> materials, boolean applyPhysics) {
        this.node = Objects.requireNonNull(node, "node");
        this.world = Objects.requireNonNull(world, "world");
        this.materials = Objects.requireNonNull(materials, "materials");
        this.applyPhysics = applyPhysics;
        this.cursor = new RefillCursor(Objects.requireNonNull(region, "region"));
    }

    ResourceNodeDefinition node() {
        return node;
    }

    World world() {
        return world;
    }

    /** @return the box this job walks - the node's region cut down to the world's height */
    Cuboid region() {
        return cursor.region();
    }

    /** @return whether every chunk of the region is loaded and held, so the walk may proceed */
    boolean isReady() {
        return ready;
    }

    /** Called once the region's chunks are loaded and held by a ticket. */
    void markReady() {
        this.ready = true;
    }

    boolean isDone() {
        return cursor.isDone();
    }

    /** Keeps the broadcast for the end of the walk, when it has become true. */
    void announceWhenDone(NodeUpdate update) {
        this.announcement = update;
    }

    /** @return what to broadcast now that the region is full, if anything */
    Optional<NodeUpdate> announcement() {
        return Optional.ofNullable(announcement).filter(NodeUpdate::hasMessage);
    }

    /** @return how many blocks this refill has actually changed so far */
    long placed() {
        return placed;
    }

    long examined() {
        return cursor.visited();
    }

    /** @return how many positions were left empty because a player was in them */
    long leftForPlayers() {
        return leftForPlayers;
    }

    /**
     * Does up to {@code budget} more positions.
     *
     * @param occupancy where players' bodies are right now; those positions are left
     *                  empty rather than filled around them
     * @return how many positions were examined, which is fewer than the budget
     *         only on the last batch
     */
    int advance(int budget, Occupancy occupancy) {
        if (!ready) {
            throw new IllegalStateException("refill of " + node.id() + " advanced before its chunks were loaded");
        }
        Objects.requireNonNull(occupancy, "occupancy");
        return cursor.advance(budget, (x, y, z) -> apply(x, y, z, occupancy));
    }

    private void apply(int x, int y, int z, Occupancy occupancy) {
        Block block = world.getBlockAt(x, y, z);
        Material current = block.getType();
        // isAir() rather than a name comparison: the server has several kinds of
        // air and the set has changed between versions (CONTRIBUTING.md section 6).
        if (!node.targets().replaces(current.name(), current.isAir())) {
            return;
        }
        String chosen = node.palette().pick(ThreadLocalRandom.current().nextDouble());
        Material material = chosen == null ? null : materials.get(chosen);
        if (material == null) {
            // The name did not resolve on this server version; the module already
            // warned about it once at load time, so say nothing more here.
            return;
        }
        if (material == current) {
            return;
        }
        if (occupancy.contains(x, y, z)) {
            // Left for the next refill: they can mine their way out of a full
            // mountain, but not breathe inside one.
            leftForPlayers++;
            return;
        }
        block.setType(material, applyPhysics);
        placed++;
    }
}
