package com.lawkeys.hcfcore.resourcenode;

import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable snapshot of {@code resourcenodes.yml}.
 *
 * @param tickSeconds  how often the schedule is examined. Unlike a capture event,
 *                     nothing here is counted down per second, so this can be
 *                     coarse
 * @param timeZone     the zone refill times are read in - explicit, so moving the
 *                     server does not silently shift every refill
 * @param blocksPerTick how much of a refill is done per tick. The whole point of
 *                     spreading the work; see {@link RefillCursor}
 * @param applyPhysics whether block placement triggers neighbour updates. Off by
 *                     default: a refill sets thousands of blocks at once, and the
 *                     Paper javadoc for {@code Block#setType(Material, boolean)}
 *                     warns that large numbers of physics updates are expensive.
 *                     Turn it on if a palette contains blocks that need gravity or
 *                     support to behave (sand, gravel)
 * @param skipOccupiedBlocks whether a refill leaves empty the blocks a player's body
 *                     is in, rather than filling the air around them and
 *                     suffocating them in the resource
 */
public record ResourceNodeSettings(boolean enabled,
                                   long tickSeconds,
                                   ZoneId timeZone,
                                   int blocksPerTick,
                                   boolean applyPhysics,
                                   boolean skipOccupiedBlocks,
                                   List<ResourceNodeDefinition> nodes) {

    public ResourceNodeSettings {
        Objects.requireNonNull(timeZone, "timeZone");
        nodes = List.copyOf(Objects.requireNonNull(nodes, "nodes"));
    }

    public static ResourceNodeSettings defaults() {
        return new ResourceNodeSettings(true, 5L, ZoneId.systemDefault(), 4_000, false, true, List.of());
    }

    public Optional<ResourceNodeDefinition> find(String id) {
        if (id == null) {
            return Optional.empty();
        }
        for (ResourceNodeDefinition node : nodes) {
            if (node.id().equalsIgnoreCase(id)) {
                return Optional.of(node);
            }
        }
        return Optional.empty();
    }
}
