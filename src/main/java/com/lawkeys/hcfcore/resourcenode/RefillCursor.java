package com.lawkeys.hcfcore.resourcenode;

import com.lawkeys.hcfcore.util.Cuboid;

import java.util.Objects;

/**
 * Walks every block of a region, a bounded batch at a time.
 *
 * <p>A refill cannot be one loop: a mountain is easily tens of thousands of
 * blocks, and setting them all inside a single tick is exactly the kind of stall
 * a PvP server cannot afford (CONTRIBUTING.md section 2). So the walk is resumable -
 * the server layer advances it by a few thousand positions per tick until it is
 * done.
 *
 * <p>It lives in the rule layer, with no server API, because "did the walk visit
 * every block exactly once, whatever the batch size" is precisely the sort of
 * off-by-one a unit test should be catching rather than a player noticing a seam
 * of missing blocks along one face of the mountain.
 *
 * <p>The walk finishes a vertical column before moving on, so consecutive
 * positions stay in the same chunk for as long as possible.
 */
public final class RefillCursor {

    /** Receives one position of the walk. Coordinates are absolute block coordinates. */
    @FunctionalInterface
    public interface Visitor {
        void visit(int x, int y, int z);
    }

    private final Cuboid region;

    private int x;
    private int y;
    private int z;
    private boolean done;
    private long visited;

    public RefillCursor(Cuboid region) {
        this.region = Objects.requireNonNull(region, "region");
        this.x = region.minX();
        this.y = region.minY();
        this.z = region.minZ();
    }

    public Cuboid region() {
        return region;
    }

    public boolean isDone() {
        return done;
    }

    public long visited() {
        return visited;
    }

    public long remaining() {
        return region.blockCount() - visited;
    }

    /** @return how far through the region the walk is, in {@code [0, 1]} */
    public double progress() {
        long total = region.blockCount();
        return total <= 0 ? 1.0d : (double) visited / total;
    }

    /**
     * Visits up to {@code limit} more positions.
     *
     * @return how many were visited, which is less than {@code limit} only on the
     *         last batch
     */
    public int advance(int limit, Visitor visitor) {
        Objects.requireNonNull(visitor, "visitor");
        int count = 0;
        while (!done && count < limit) {
            visitor.visit(x, y, z);
            count++;
            visited++;
            step();
        }
        return count;
    }

    private void step() {
        if (++y <= region.maxY()) {
            return;
        }
        y = region.minY();
        if (++z <= region.maxZ()) {
            return;
        }
        z = region.minZ();
        if (++x <= region.maxX()) {
            return;
        }
        // Past the far corner: the walk is over. x is left out of bounds on purpose,
        // so a caller that ignores isDone() cannot silently start a second lap.
        done = true;
    }
}
