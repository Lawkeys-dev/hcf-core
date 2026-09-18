package com.lawkeys.hcfcore.util;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * An immutable, Bukkit-free reference to one chunk in one world.
 *
 * <p>This is the unit of territory ownership (ARCHITECTURE.md section 4,
 * {@code team_claims}). Like {@link WorldPosition} it avoids the server API so
 * the claim rules stay unit-testable, and it is used as a hash-map key on the
 * block-event hot path, so equality and hashing come straight from the record.
 *
 * @param x chunk X, i.e. block X shifted right by 4
 * @param z chunk Z, i.e. block Z shifted right by 4
 */
public record ChunkPosition(String world, int x, int z) {

    /** Bit shift between block and chunk coordinates: a chunk is 16 blocks wide. */
    private static final int CHUNK_SHIFT = 4;

    public ChunkPosition {
        Objects.requireNonNull(world, "world");
    }

    /**
     * @param blockX block X coordinate
     * @param blockZ block Z coordinate
     * @return the chunk containing that block; arithmetic shift keeps negative
     *         coordinates correct, where integer division would round toward zero
     */
    public static ChunkPosition fromBlock(String world, int blockX, int blockZ) {
        return new ChunkPosition(world, toChunk(blockX), toChunk(blockZ));
    }

    /**
     * @return the chunk coordinate containing that block coordinate
     *
     * <p>Exposed so callers that only need the arithmetic - {@link Cuboid} testing
     * whether a chunk overlaps a region, for instance - do not have to build a
     * {@code ChunkPosition} or restate the shift and get the negative case wrong.
     */
    public static int toChunk(int blockCoordinate) {
        return blockCoordinate >> CHUNK_SHIFT;
    }

    /** @return the four orthogonally adjacent chunks. */
    public Set<ChunkPosition> neighbours() {
        Set<ChunkPosition> neighbours = new LinkedHashSet<>(4);
        neighbours.add(new ChunkPosition(world, x + 1, z));
        neighbours.add(new ChunkPosition(world, x - 1, z));
        neighbours.add(new ChunkPosition(world, x, z + 1));
        neighbours.add(new ChunkPosition(world, x, z - 1));
        return neighbours;
    }

    /** @return the block coordinate of this chunk's north-west corner. */
    public int minBlockX() {
        return x << CHUNK_SHIFT;
    }

    public int minBlockZ() {
        return z << CHUNK_SHIFT;
    }

    @Override
    public String toString() {
        return world + " [" + x + ", " + z + ']';
    }
}
