package com.lawkeys.hcfcore.resourcenode;

import com.lawkeys.hcfcore.util.Cuboid;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * The blocks players' bodies are in, around one region, for one step of a refill.
 *
 * <p>A refill fills air, and a player standing in the region is standing in air.
 * Without this, a refill landing while someone mines would put the resource where
 * their head is and suffocate them - a death nobody caused, at the one moment the
 * server told every team to come and fight. The server layer feeds in bodies; the
 * refill leaves their blocks empty.
 *
 * <p>Pure Java, from bounding-box numbers rather than an entity, so the geometry -
 * which blocks a body overlaps, down to the edge cases - is tested without a
 * server.
 */
public final class Occupancy {

    private final Cuboid region;
    private final Set<Long> blocks = new HashSet<>();

    /** @param region the box being refilled; bodies nowhere near it are ignored */
    public Occupancy(Cuboid region) {
        this.region = Objects.requireNonNull(region, "region");
    }

    /**
     * Records every block a body's bounding box overlaps.
     *
     * <p>From the box rather than the feet: a player straddling two columns, or
     * crouching, or swimming, occupies more than the block under them.
     */
    public void addBody(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (maxX < region.minX() || minX > region.maxX() + 1
                || maxY < region.minY() || minY > region.maxY() + 1
                || maxZ < region.minZ() || minZ > region.maxZ() + 1) {
            return;
        }
        for (int x = first(minX); x <= last(minX, maxX); x++) {
            for (int y = first(minY); y <= last(minY, maxY); y++) {
                for (int z = first(minZ); z <= last(minZ, maxZ); z++) {
                    blocks.add(key(x, y, z));
                }
            }
        }
    }

    /** @return whether a body is in that block */
    public boolean contains(int x, int y, int z) {
        return !blocks.isEmpty() && blocks.contains(key(x, y, z));
    }

    public boolean isEmpty() {
        return blocks.isEmpty();
    }

    /** The block holding the box's low face. Floor, not a cast: -0.5 is in block -1. */
    private static int first(double min) {
        return (int) Math.floor(min);
    }

    /**
     * The block holding the box's high face. A face exactly on a block boundary -
     * a box ending at 4.0 - does not reach into block 4; at least the first block
     * always counts.
     */
    private static int last(double min, double max) {
        return Math.max(first(min), (int) Math.ceil(max) - 1);
    }

    /**
     * One {@code long} per block: 26 bits for x and z (the world border sits at 30
     * million, under 2^25) and 12 for y (a world spans a few hundred blocks).
     */
    private static long key(int x, int y, int z) {
        return ((x & 0x3FFFFFFL) << 38) | ((z & 0x3FFFFFFL) << 12) | (y & 0xFFFL);
    }
}
