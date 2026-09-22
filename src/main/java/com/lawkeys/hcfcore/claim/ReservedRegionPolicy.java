package com.lawkeys.hcfcore.claim;


import java.util.Optional;

/**
 * Answers whether another system has reserved land, putting it out of reach of
 * team claims.
 *
 * <p>The third seam of the same family as {@link RaidabilityPolicy} and
 * {@link TeleportGuard}, and it exists for the same reason (ARCHITECTURE.md
 * section 14): FEATURES.md section 6 requires a resource node's region to be
 * "non claimable", but {@code resourcenode/} is written long after
 * {@code claim/}. Rather than teach the claim module what a mountain is - or
 * worse, store a "reserved" flag on chunks that nothing keeps up to date - the
 * claim module asks this question while claiming, and whoever owns such regions
 * answers it at startup.
 *
 * <p>The answer carries the region's display name rather than a boolean so the
 * refusal can tell the player <em>what</em> is in the way; being told "no"
 * without being told why reads as a broken server.
 */
@FunctionalInterface
public interface ReservedRegionPolicy {

    /**
     * @return the display name of a region any block of that rectangle - the claim a
     *         team is trying to make, full height - belongs to, or empty when the
     *         land is free
     */
    Optional<String> reservedRegionIn(String world, int minX, int minZ, int maxX, int maxZ);

    /**
     * Asked about one block of unclaimed land, before the warzone refuses to let it
     * be built on or mined.
     *
     * <p>A region another system owns is governed by that system's own rules, not
     * the warzone's: a Mountain inside the warzone must stay minable, which the
     * warzone's "no building" would otherwise forbid before the Mountain's own
     * listener is even asked. Only the blocks inside the region are handed over;
     * the rest of a chunk it merely touches stays warzone.
     *
     * <p>Runs on block events; implementations must be cheap.
     *
     * @return {@code true} if that block belongs to such a region
     */
    default boolean isInReservedRegion(String world, int x, int y, int z) {
        return false;
    }

    /** Nothing is reserved. Active until a module that owns regions installs itself. */
    ReservedRegionPolicy NONE = (world, minX, minZ, maxX, maxZ) -> Optional.empty();
}
