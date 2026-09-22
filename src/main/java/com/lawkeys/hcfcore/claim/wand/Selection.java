package com.lawkeys.hcfcore.claim.wand;

import java.util.Objects;

/**
 * The two corners a claiming wand has picked so far, block-precise. Pure.
 *
 * <p>A corner is the block that was clicked; the rectangle between the two - both
 * included - is what gets claimed. Heights are kept too: an event zone is a box, and
 * takes them.
 */
public record Selection(String world, Corner first, Corner second) {

    /** One clicked block. */
    public record Corner(String world, int x, int y, int z) {

        public Corner {
            Objects.requireNonNull(world, "world");
        }
    }

    public static Selection empty() {
        return new Selection(null, null, null);
    }

    /** @return this selection with that corner picked; picking it in another world starts over */
    public Selection with(int which, Corner corner) {
        Objects.requireNonNull(corner, "corner");
        boolean sameWorld = world == null || world.equals(corner.world());
        Corner keptFirst = sameWorld ? first : null;
        Corner keptSecond = sameWorld ? second : null;
        return which == 1
                ? new Selection(corner.world(), corner, keptSecond)
                : new Selection(corner.world(), keptFirst, corner);
    }

    public boolean isComplete() {
        return first != null && second != null;
    }

    public int minX() {
        return Math.min(first.x(), second.x());
    }

    public int maxX() {
        return Math.max(first.x(), second.x());
    }

    public int minZ() {
        return Math.min(first.z(), second.z());
    }

    public int maxZ() {
        return Math.max(first.z(), second.z());
    }

    public int minY() {
        return Math.min(first.y(), second.y());
    }

    public int maxY() {
        return Math.max(first.y(), second.y());
    }

    /** @return east-west size in blocks, both corners included */
    public int width() {
        return maxX() - minX() + 1;
    }

    /** @return north-south size in blocks */
    public int length() {
        return maxZ() - minZ() + 1;
    }

    public long area() {
        return (long) width() * length();
    }
}
