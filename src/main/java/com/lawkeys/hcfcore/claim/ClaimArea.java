package com.lawkeys.hcfcore.claim;

import com.lawkeys.hcfcore.util.ChunkPosition;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * One claim: a rectangle of land, block-precise, the full height of the world,
 * owned by one team.
 *
 * <p>Territory is drawn this way since 22/09/2026 - the project owner's choice,
 * the traditional HCF claim made with a wand from two corners - where it used to be
 * whole chunks. Both corners are inside the claim: a claim from {@code (0, 0)} to
 * {@code (4, 4)} is five blocks by five.
 *
 * <p>Ownership is the persistent half of the model FEATURES.md section 3 calls for:
 * set when the land is claimed, cleared only by an unclaim or a disband, never by a
 * raid.
 *
 * @param id        the claim's own identity, so one of a team's claims can be named
 *                  and released on its own
 * @param pricePaid what the team paid for it, from which an unclaim refunds: the
 *                  price may change between the two, and a refund at the new price
 *                  would let a team claim cheap and sell dear
 */
public record ClaimArea(UUID id, UUID teamId, String world, int minX, int minZ, int maxX, int maxZ,
                        double pricePaid, long claimedAt) {

    public ClaimArea {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(teamId, "teamId");
        Objects.requireNonNull(world, "world");
        if (minX > maxX || minZ > maxZ) {
            throw new IllegalArgumentException("corners out of order: " + minX + "," + minZ + " to " + maxX + "," + maxZ);
        }
        if (!(pricePaid >= 0) || !Double.isFinite(pricePaid)) {
            throw new IllegalArgumentException("price paid must be a finite amount, at least 0: " + pricePaid);
        }
    }

    /** @return the rectangle between two corners given in any order */
    public static ClaimArea between(UUID teamId, String world, int x1, int z1, int x2, int z2,
                                    double pricePaid, long claimedAt) {
        return new ClaimArea(UUID.randomUUID(), teamId, world,
                Math.min(x1, x2), Math.min(z1, z2), Math.max(x1, x2), Math.max(z1, z2), pricePaid, claimedAt);
    }

    public boolean contains(String world, int x, int z) {
        return this.world.equals(world) && x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    /** @return the east-west size, in blocks, both edges included */
    public int width() {
        return maxX - minX + 1;
    }

    /** @return the north-south size, in blocks */
    public int length() {
        return maxZ - minZ + 1;
    }

    /** @return the surface, in blocks: what a claim is priced by */
    public long area() {
        return (long) width() * length();
    }

    /** @return whether the two share at least one block */
    public boolean overlaps(ClaimArea other) {
        return world.equals(other.world)
                && minX <= other.maxX && other.minX <= maxX
                && minZ <= other.maxZ && other.minZ <= maxZ;
    }

    /**
     * @return how many blocks of land lie between the two along the shortest way,
     *         {@code 0} when they touch or overlap - the buffer between teams is
     *         measured with this. Different worlds are infinitely far apart
     */
    public int gapTo(ClaimArea other) {
        if (!world.equals(other.world)) {
            return Integer.MAX_VALUE;
        }
        int dx = Math.max(0, Math.max(other.minX - maxX - 1, minX - other.maxX - 1));
        int dz = Math.max(0, Math.max(other.minZ - maxZ - 1, minZ - other.maxZ - 1));
        return Math.max(dx, dz);
    }

    /**
     * @return whether the two share an edge - side by side with no block between,
     *         along at least one block of border. Touching only at a corner does
     *         not connect territory, as with chunks before
     */
    public boolean touches(ClaimArea other) {
        if (!world.equals(other.world) || overlaps(other)) {
            return false;
        }
        boolean sideBySideX = (other.minX == maxX + 1 || minX == other.maxX + 1)
                && minZ <= other.maxZ && other.minZ <= maxZ;
        boolean sideBySideZ = (other.minZ == maxZ + 1 || minZ == other.maxZ + 1)
                && minX <= other.maxX && other.minX <= maxX;
        return sideBySideX || sideBySideZ;
    }

    /** @return every chunk this claim covers, even in part: the lookup index is kept by chunk */
    public List<ChunkPosition> chunks() {
        List<ChunkPosition> chunks = new ArrayList<>();
        for (int cx = ChunkPosition.toChunk(minX); cx <= ChunkPosition.toChunk(maxX); cx++) {
            for (int cz = ChunkPosition.toChunk(minZ); cz <= ChunkPosition.toChunk(maxZ); cz++) {
                chunks.add(new ChunkPosition(world, cx, cz));
            }
        }
        return chunks;
    }

    /** @return the block in the middle, rounded down: where a waypoint or a teleport aims */
    public int centreX() {
        return Math.floorDiv(minX + maxX, 2);
    }

    public int centreZ() {
        return Math.floorDiv(minZ + maxZ, 2);
    }

    @Override
    public String toString() {
        return world + " [" + minX + ", " + minZ + " -> " + maxX + ", " + maxZ + ']';
    }
}
