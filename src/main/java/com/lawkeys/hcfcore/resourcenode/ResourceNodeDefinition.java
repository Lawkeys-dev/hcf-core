package com.lawkeys.hcfcore.resourcenode;

import com.lawkeys.hcfcore.util.Cuboid;

import java.util.List;
import java.util.Objects;

/**
 * One configured resource node, as read from {@code resourcenodes.yml}.
 *
 * <p>Family B of ARCHITECTURE.md section 9, and deliberately <em>not</em> a
 * subtype of anything in {@code events/}: there is no zone to hold, no winner and
 * no reward here. A mountain is a timer, a box and a list of blocks. Sharing the
 * capture abstraction would have meant carrying a "winning team" through code
 * that can never have one.
 *
 * <p>Like a capture event, a node is data rather than behaviour: a second
 * mountain - Ore Mountain beside Glowstone Mountain - is a block of YAML, not a
 * subclass.
 *
 * @param id            stable key, used in commands, config and messages
 * @param displayName   what players see, colour codes included
 * @param region        the box that refills
 * @param palette       what is put back, and in what proportion
 * @param targets       which blocks a refill may overwrite
 * @param schedule      when it refills by itself; empty means staff-only
 * @param announceBeforeSeconds how long before a refill to warn, in seconds, any
 *                      order. This is what turns a refill into the PvP hotspot
 *                      FEATURES.md asks for: teams need notice to converge
 * @param announceRefill whether the refill itself is broadcast
 * @param fillOnStart   whether the node is filled once when the server starts
 * @param preventBuild  whether placing blocks in the region is refused
 * @param breakPolicy   what may be mined in the region
 * @param preventClaim  whether teams are refused claims overlapping the region
 * @param preventExplosions whether explosions are stopped from breaking the
 *                      region. On by default, and for the same reason the break
 *                      policy exists: a structure players cannot mine but can
 *                      blow up is a structure that goes away
 * @param claim         the server team whose land the mountain is, blank for a
 *                      region given by its corners. A claimed node's region is
 *                      the land of that team between the node's heights - its
 *                      world and columns come from the claims, resolved while the
 *                      server runs ({@link ClaimedRegions}); until then only its
 *                      heights are meaningful
 */
public record ResourceNodeDefinition(String id,
                                     String displayName,
                                     Cuboid region,
                                     BlockPalette palette,
                                     RefillTargets targets,
                                     RefillSchedule schedule,
                                     List<Long> announceBeforeSeconds,
                                     boolean announceRefill,
                                     boolean fillOnStart,
                                     boolean preventBuild,
                                     BreakPolicy breakPolicy,
                                     boolean preventClaim,
                                     boolean preventExplosions,
                                     String claim) {

    public ResourceNodeDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(palette, "palette");
        Objects.requireNonNull(targets, "targets");
        Objects.requireNonNull(schedule, "schedule");
        Objects.requireNonNull(breakPolicy, "breakPolicy");
        claim = claim == null ? "" : claim.trim();
        announceBeforeSeconds =
                List.copyOf(Objects.requireNonNull(announceBeforeSeconds, "announceBeforeSeconds"));
        if (palette.isEmpty()) {
            throw new IllegalArgumentException("node " + id + " has nothing to refill with");
        }
    }

    /** A node given by its corners, as before mountains could be claimed. */
    public ResourceNodeDefinition(String id, String displayName, Cuboid region, BlockPalette palette,
                                  RefillTargets targets, RefillSchedule schedule, List<Long> announceBeforeSeconds,
                                  boolean announceRefill, boolean fillOnStart, boolean preventBuild,
                                  BreakPolicy breakPolicy, boolean preventClaim, boolean preventExplosions) {
        this(id, displayName, region, palette, targets, schedule, announceBeforeSeconds, announceRefill,
                fillOnStart, preventBuild, breakPolicy, preventClaim, preventExplosions, "");
    }

    /** @return whether the region is a server team's land rather than two corners */
    public boolean isClaimed() {
        return !claim.isEmpty();
    }

    /** @return this node over another region: a claimed node once its land is known */
    public ResourceNodeDefinition withRegion(Cuboid newRegion) {
        return new ResourceNodeDefinition(id, displayName, newRegion, palette, targets, schedule,
                announceBeforeSeconds, announceRefill, fillOnStart, preventBuild, breakPolicy, preventClaim,
                preventExplosions, claim);
    }

    /** @return whether that block may be mined here */
    public boolean allowsBreaking(String material) {
        return breakPolicy.allows(palette, material);
    }
}
