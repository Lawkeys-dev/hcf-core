package com.lawkeys.hcfcore.resourcenode;

import com.lawkeys.hcfcore.claim.ReservedRegionPolicy;
import com.lawkeys.hcfcore.util.ChunkPosition;
import com.lawkeys.hcfcore.util.Durations;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The resource node rule engine - family B of ARCHITECTURE.md section 9
 * (Mountain, Glowstone Mountain, Ore Mountain).
 *
 * <p>Pure Java with no server API, like every other manager here. It decides
 * <em>when</em> a region refills and <em>what may happen</em> inside it; putting
 * blocks in the world is the server layer's job.
 *
 * <p><strong>This module deliberately shares nothing with {@code events/}.</strong>
 * ARCHITECTURE.md section 9 is explicit that a mountain must not inherit the
 * capture abstraction, and the reason shows up the moment you try: there is no
 * holder, no contest, no countdown to win and nobody to reward. What the two
 * families do share is a box ({@link com.lawkeys.hcfcore.util.Cuboid}) and a
 * clock, which are value types, not an abstraction.
 *
 * <p><strong>Nothing is persisted</strong>, and nothing needs to be: refill times
 * are absolute (see {@link RefillSchedule}), so a restart cannot make a node lose
 * its place. The only state kept in memory is when each node was last filled,
 * which is a display detail.
 *
 * <p>It answers {@link ReservedRegionPolicy} for the claim module, which is how a
 * node's region becomes unclaimable without {@code claim/} knowing what a
 * mountain is.
 */
public final class ResourceNodeManager implements ReservedRegionPolicy {

    private final Supplier<ResourceNodeSettings> settings;
    private final LongSupplier clock;

    /**
     * Upper bound of the window already examined for refills.
     *
     * <p>Zero until the first tick, which only records "now" and fires nothing:
     * otherwise a server booting at 12:01 would immediately refill everything the
     * midnight-anchored schedule had passed while it was down.
     */
    private volatile long lastScheduleCheck;

    public ResourceNodeManager(Supplier<ResourceNodeSettings> settings) {
        this(settings, System::currentTimeMillis);
    }

    /** @param clock epoch-millis source; injectable so refills can be tested without waiting hours */
    public ResourceNodeManager(Supplier<ResourceNodeSettings> settings, LongSupplier clock) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    private ResourceNodeSettings config() {
        return settings.get();
    }

    // ------------------------------------------------------------------
    // Reading
    // ------------------------------------------------------------------

    /** @return every configured node, whether or not the module is enabled */
    public List<ResourceNodeDefinition> getNodes() {
        return config().nodes();
    }

    /**
     * @return the nodes that are actually in force
     *
     * <p>Every question that decides something - may this block be broken, may
     * this chunk be claimed - goes through here rather than through
     * {@link #getNodes()}. Otherwise {@code enabled: false} would silently be a
     * half-measure: refills would stop while the regions kept refusing builds and
     * claims, and the command would be telling operators the module was off while
     * it was still saying no to their players.
     */
    private List<ResourceNodeDefinition> enforcedNodes() {
        ResourceNodeSettings config = config();
        return config.enabled() ? config.nodes() : List.of();
    }

    public Optional<ResourceNodeDefinition> getNode(String id) {
        return config().find(id);
    }

    /** @return the node whose region contains that point, if any */
    public Optional<ResourceNodeDefinition> nodeAt(String world, double x, double y, double z) {
        if (world == null) {
            return Optional.empty();
        }
        for (ResourceNodeDefinition node : enforcedNodes()) {
            if (node.region().contains(world, x, y, z)) {
                return Optional.of(node);
            }
        }
        return Optional.empty();
    }

    /** @return the node whose region contains that block, if any */
    public Optional<ResourceNodeDefinition> nodeAtBlock(String world, int x, int y, int z) {
        if (world == null) {
            return Optional.empty();
        }
        for (ResourceNodeDefinition node : enforcedNodes()) {
            if (node.region().containsBlock(world, x, y, z)) {
                return Optional.of(node);
            }
        }
        return Optional.empty();
    }

    /** @return the node whose region overlaps that chunk and refuses claims, if any */
    public Optional<ResourceNodeDefinition> claimBlockingNodeAt(ChunkPosition chunk) {
        if (chunk == null) {
            return Optional.empty();
        }
        for (ResourceNodeDefinition node : enforcedNodes()) {
            if (node.preventClaim() && node.region().overlaps(chunk)) {
                return Optional.of(node);
            }
        }
        return Optional.empty();
    }

    /**
     * @return whether an explosion must be stopped from breaking that block
     *
     * <p>Kept beside the other rules rather than in the listener so that "does TNT
     * take a chunk out of the mountain?" is answerable without a server.
     */
    public boolean isExplosionProtected(String world, int x, int y, int z) {
        if (world == null) {
            return false;
        }
        for (ResourceNodeDefinition node : enforcedNodes()) {
            if (node.preventExplosions() && node.region().containsBlock(world, x, y, z)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return whether nothing may be built at that block - the rule behind the
     *         changes no player makes directly: a piston pushing blocks into or out
     *         of the region, a liquid flowing into it
     *
     * <p>Kept here for the same reason as {@link #isExplosionProtected}: "can lava
     * poured outside the mountain flow in?" is a rule, and rules are answered
     * without a server.
     */
    public boolean isBuildProtected(String world, int x, int y, int z) {
        if (world == null) {
            return false;
        }
        for (ResourceNodeDefinition node : enforcedNodes()) {
            if (node.preventBuild() && node.region().containsBlock(world, x, y, z)) {
                return true;
            }
        }
        return false;
    }

    /** Every block of an enforced node's region is governed by the node, not by the warzone. */
    @Override
    public boolean isInReservedRegion(String world, int x, int y, int z) {
        return nodeAtBlock(world, x, y, z).isPresent();
    }

    @Override
    public Optional<String> reservedRegionAt(ChunkPosition chunk) {
        return claimBlockingNodeAt(chunk).map(ResourceNodeDefinition::displayName);
    }

    /** @return the next time this node refills by itself, or empty when only staff can */
    public Optional<ZonedDateTime> getNextRefill(ResourceNodeDefinition node) {
        return getNextRefill(node, clock.getAsLong());
    }

    Optional<ZonedDateTime> getNextRefill(ResourceNodeDefinition node, long now) {
        Objects.requireNonNull(node, "node");
        ZoneId zone = config().timeZone();
        return node.schedule().next(Instant.ofEpochMilli(now).atZone(zone));
    }

    /** @return the nodes an operator asked to have filled as soon as the server is up */
    public List<ResourceNodeDefinition> getStartupFills() {
        List<ResourceNodeDefinition> nodes = new ArrayList<>();
        for (ResourceNodeDefinition node : enforcedNodes()) {
            if (node.fillOnStart()) {
                nodes.add(node);
            }
        }
        return nodes;
    }

    // ------------------------------------------------------------------
    // The tick
    // ------------------------------------------------------------------

    /**
     * Examines the window since the previous tick.
     *
     * @return the refills to perform and the warnings to broadcast, in that order
     *         of interest; an empty list is the normal case
     */
    public List<NodeUpdate> tick() {
        long now = clock.getAsLong();
        long previous = lastScheduleCheck;
        // Recorded before anything else, and also while the module is disabled: a
        // disabled period must be frozen, not deferred. Left unmoved, re-enabling
        // after two days down would fire every refill those two days contained.
        lastScheduleCheck = now;

        ResourceNodeSettings config = config();
        if (!config.enabled() || previous == 0L || now <= previous) {
            return List.of();
        }

        ZoneId zone = config.timeZone();
        List<NodeUpdate> updates = new ArrayList<>();
        for (ResourceNodeDefinition node : config.nodes()) {
            if (node.schedule().isEmpty()) {
                continue;
            }
            if (node.schedule().occursWithin(zone, previous, now)) {
                updates.add(refillUpdate(node));
            }
            // A warning is the same question asked about a window shifted forwards:
            // "would a refill have landed in this second, if this second were N
            // seconds later?". Deriving it from the schedule rather than from a
            // countdown of our own is what keeps the warning and the refill from
            // ever disagreeing.
            for (long mark : node.announceBeforeSeconds()) {
                long offset = mark * 1000L;
                if (node.schedule().occursWithin(zone, previous + offset, now + offset)) {
                    updates.add(NodeUpdate.of(NodeUpdate.Type.REFILL_SOON, node.id(),
                            ResourceNodeMessages.REFILL_SOON,
                            "node", node.displayName(),
                            "time", Durations.format(mark)));
                }
            }
        }
        return updates;
    }

    /**
     * Refills a node now, whatever its schedule says.
     *
     * @return the instruction for the server layer, or empty when there is no such
     *         node
     */
    public Optional<NodeUpdate> forceRefill(String nodeId) {
        return config().find(nodeId).map(ResourceNodeManager::refillUpdate);
    }

    private static NodeUpdate refillUpdate(ResourceNodeDefinition node) {
        // A silent node still refills: whether to speak is a rule, decided here
        // where it is testable, not in the listener that renders the text.
        String messageKey = node.announceRefill() ? ResourceNodeMessages.REFILLED : "";
        return NodeUpdate.of(NodeUpdate.Type.REFILL_DUE, node.id(), messageKey,
                "node", node.displayName(),
                "world", node.region().world());
    }

    /** Forgets the window, so a reload does not fire refills whose time already passed. */
    public void resetScheduleWindow() {
        this.lastScheduleCheck = 0L;
    }

    private static String key(String nodeId) {
        return nodeId.toLowerCase(Locale.ROOT);
    }
}
