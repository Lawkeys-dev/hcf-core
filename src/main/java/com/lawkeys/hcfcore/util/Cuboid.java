package com.lawkeys.hcfcore.util;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * An axis-aligned box of blocks in one world.
 *
 * <p>Bukkit-free on purpose, like {@link WorldPosition} and {@link ChunkPosition}:
 * regions are read from config, held by pure rule engines and covered by unit
 * tests that run without a server. Turning a player's {@code Location} into a
 * containment check belongs to a module's server layer.
 *
 * <p>It lives in {@code util/} rather than in one module because two unrelated
 * systems need the same geometry and neither owns it: a capture event's zone
 * (ARCHITECTURE.md section 9, family A) and a resource node's region (family B).
 * Those two families must stay independent - a mountain has nothing to capture -
 * so the box they share is a value type, not an abstraction either of them
 * exports.
 *
 * <p>Bounds are normalised on construction, so an operator may write the two
 * corners of a region in whichever order they walked them - a config that reads
 * {@code x1: 40, x2: 10} means the same region as {@code x1: 10, x2: 40} rather
 * than an empty one.
 */
public record Cuboid(String world,
                     int minX, int minY, int minZ,
                     int maxX, int maxY, int maxZ) {

    public Cuboid {
        Objects.requireNonNull(world, "world");
    }

    /** @return a box from two opposite corners, in any order */
    public static Cuboid between(String world,
                                 int x1, int y1, int z1,
                                 int x2, int y2, int z2) {
        return new Cuboid(world,
                Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
    }

    /**
     * @return whether the point is inside the box
     *
     * <p>Bounds are inclusive: a block at exactly {@code maxX} is in the box, so
     * the region an operator sees in-game matches the one they configured.
     */
    public boolean contains(String world, double x, double y, double z) {
        if (!this.world.equalsIgnoreCase(world)) {
            return false;
        }
        // Floor rather than cast: a cast truncates towards zero, which would put
        // a player standing at x = -0.5 into block 0 instead of block -1 and make
        // the box one block wider on its negative side.
        return containsBlock(world,
                (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    /** @return whether that exact block belongs to the box */
    public boolean containsBlock(String world, int x, int y, int z) {
        return this.world.equalsIgnoreCase(world)
                && x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    /**
     * @return whether any block of that chunk belongs to the box
     *
     * <p>Used to refuse claiming land a region sits on: a chunk that merely
     * clips a corner of the region still overlaps it.
     */
    public boolean overlapsChunk(String world, int chunkX, int chunkZ) {
        return this.world.equalsIgnoreCase(world)
                && chunkX >= minChunkX() && chunkX <= maxChunkX()
                && chunkZ >= minChunkZ() && chunkZ <= maxChunkZ();
    }

    /**
     * The chunks the box spans, as bounds - what a caller loading or holding the
     * region's chunks iterates over.
     */
    public int minChunkX() {
        return ChunkPosition.toChunk(minX);
    }

    public int maxChunkX() {
        return ChunkPosition.toChunk(maxX);
    }

    public int minChunkZ() {
        return ChunkPosition.toChunk(minZ);
    }

    public int maxChunkZ() {
        return ChunkPosition.toChunk(maxZ);
    }

    /** @return whether that chunk overlaps the box */
    public boolean overlaps(ChunkPosition chunk) {
        return chunk != null && overlapsChunk(chunk.world(), chunk.x(), chunk.z());
    }

    /**
     * Cuts the box down to a vertical range.
     *
     * @param floor   lowest block that exists, inclusive
     * @param ceiling highest block that exists, inclusive
     * @return the part of the box inside that range, or empty when none of it is
     *
     * <p>A region configured taller than its world is not an error worth refusing -
     * an operator writing {@code y: 0} to {@code y: 320} in a world that stops at
     * 256 means the mountain, not the sky above it. But walking the part that
     * cannot hold a block is pure waste, so it is cut here rather than examined
     * block by block.
     */
    public Optional<Cuboid> withYClampedTo(int floor, int ceiling) {
        int newMin = Math.max(minY, floor);
        int newMax = Math.min(maxY, ceiling);
        if (newMin > newMax) {
            return Optional.empty();
        }
        if (newMin == minY && newMax == maxY) {
            return Optional.of(this);
        }
        return Optional.of(new Cuboid(world, minX, newMin, minZ, maxX, newMax, maxZ));
    }

    public int sizeX() {
        return maxX - minX + 1;
    }

    public int sizeY() {
        return maxY - minY + 1;
    }

    public int sizeZ() {
        return maxZ - minZ + 1;
    }

    /**
     * @return how many blocks the box holds
     *
     * <p>A {@code long}: a region a few hundred blocks on a side already
     * overflows an {@code int}, and this number is used to bound the work of a
     * refill.
     */
    public long blockCount() {
        return (long) sizeX() * sizeY() * sizeZ();
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%s [%d,%d,%d -> %d,%d,%d]",
                world, minX, minY, minZ, maxX, maxY, maxZ);
    }
}
