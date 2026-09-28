package com.lawkeys.hcfcore.resourcenode;

import com.lawkeys.hcfcore.claim.ClaimArea;
import com.lawkeys.hcfcore.util.Cuboid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * A mountain that is claimed land (the owner's request of 28/09/2026): its region is
 * the land of a server team, made and changed with the claiming wand like any
 * other server land, between the heights the node gives.
 *
 * <p>The claims are the claim module's and change while the server runs, so the
 * region is worked out again on every tick of the module rather than read once:
 * a claim drawn, extended or released is the mountain's new shape seconds later.
 * Several claims make one region, the box around them - keep a mountain to one
 * claim, or to claims side by side, or the box takes in the land between.
 *
 * <p>Pure Java: the claims in, the box out.
 */
public final class ClaimedRegions {

    /** The world of a claimed node whose land is not known yet. */
    public static final String UNRESOLVED = "";

    private ClaimedRegions() {
    }

    /**
     * @param claims the land of the node's team, in any world
     * @return the node's region: the box around the team's claims in the world of
     *         its first, between the node's heights; empty when the team holds nothing
     */
    public static Optional<Cuboid> resolve(ResourceNodeDefinition node, Collection<ClaimArea> claims) {
        if (claims.isEmpty()) {
            return Optional.empty();
        }
        String world = claims.iterator().next().world();
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (ClaimArea claim : claims) {
            if (!claim.world().equals(world)) {
                continue;
            }
            minX = Math.min(minX, claim.minX());
            minZ = Math.min(minZ, claim.minZ());
            maxX = Math.max(maxX, claim.maxX());
            maxZ = Math.max(maxZ, claim.maxZ());
        }
        Cuboid heights = node.region();
        return Optional.of(Cuboid.between(world, minX, heights.minY(), minZ, maxX, heights.maxY(), maxZ));
    }

    /**
     * The nodes as they stand now: a claimed node over its team's land, dropped while
     * the team holds none - nothing to refill, nothing to protect.
     *
     * @param landOf the claims of a server team, by name; empty for no such team
     * @param missing told the id of each claimed node left out
     */
    public static List<ResourceNodeDefinition> resolveAll(List<ResourceNodeDefinition> nodes,
                                                          Function<String, Collection<ClaimArea>> landOf,
                                                          java.util.function.Consumer<ResourceNodeDefinition> missing) {
        List<ResourceNodeDefinition> resolved = new ArrayList<>(nodes.size());
        for (ResourceNodeDefinition node : nodes) {
            if (!node.isClaimed()) {
                resolved.add(node);
                continue;
            }
            Optional<Cuboid> region = resolve(node, landOf.apply(node.claim()));
            if (region.isPresent()) {
                resolved.add(node.withRegion(region.get()));
            } else {
                missing.accept(node);
            }
        }
        return resolved;
    }
}
