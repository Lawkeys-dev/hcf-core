package com.lawkeys.hcfcore.resourcenode;

import com.lawkeys.hcfcore.util.ChunkPosition;
import com.lawkeys.hcfcore.util.Cuboid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Tracks which refills are holding which chunks loaded.
 *
 * <p>A refill spreads its work over many ticks, and a chunk that unloads halfway
 * through would be loaded again - synchronously, on the main thread - by the next
 * block it touches. So the server layer holds a plugin chunk ticket on the
 * region's chunks for as long as the refill runs.
 *
 * <p>The Paper javadoc allows a plugin <em>one</em> ticket per chunk, and two
 * mountains can share a chunk and refill at the same moment. Adding and removing
 * tickets region by region would let the first refill to finish release the chunk
 * under the second. This class says which chunks just gained their first holder
 * and which just lost their last, and the caller adds or removes tickets for
 * exactly those.
 *
 * <p>Holders are tracked by identity, not merely counted. A count cannot tell who
 * holds a chunk, so a release by something that never held it would still take a
 * shared chunk from the refill that does. Here a release only ever gives back what
 * that holder took, needs no region to be passed again, and is harmless twice.
 *
 * <p>Pure Java, so all of this is tested without a server. Main thread only, like
 * the refills it serves.
 */
public final class ChunkHolds {

    private final Map<ChunkPosition, Set<Object>> holdersByChunk = new HashMap<>();
    private final Map<Object, List<ChunkPosition>> chunksByHolder = new HashMap<>();

    /**
     * Records {@code holder} as holding every chunk of the region.
     *
     * @param holder compared with {@code equals}; the server layer passes the refill
     *               job itself. A holder already holding something is left as it is
     * @return the chunks that had no holder until now - the ones to add a ticket to
     */
    public List<ChunkPosition> hold(Object holder, Cuboid region) {
        Objects.requireNonNull(holder, "holder");
        if (chunksByHolder.containsKey(holder)) {
            return List.of();
        }
        List<ChunkPosition> chunks = chunksOf(region);
        chunksByHolder.put(holder, chunks);
        List<ChunkPosition> first = new ArrayList<>();
        for (ChunkPosition chunk : chunks) {
            Set<Object> holders = holdersByChunk.computeIfAbsent(chunk, ignored -> new HashSet<>());
            if (holders.isEmpty()) {
                first.add(chunk);
            }
            holders.add(holder);
        }
        return first;
    }

    /**
     * Gives back everything {@code holder} took.
     *
     * @return the chunks nobody holds any more - the ones to remove the ticket from.
     *         Empty for a holder that holds nothing, so a stray or repeated release
     *         can never free a chunk another refill still needs
     */
    public List<ChunkPosition> release(Object holder) {
        List<ChunkPosition> chunks = chunksByHolder.remove(holder);
        if (chunks == null) {
            return List.of();
        }
        List<ChunkPosition> last = new ArrayList<>();
        for (ChunkPosition chunk : chunks) {
            Set<Object> holders = holdersByChunk.get(chunk);
            if (holders != null && holders.remove(holder) && holders.isEmpty()) {
                holdersByChunk.remove(chunk);
                last.add(chunk);
            }
        }
        return last;
    }

    /** @return whether anything is held at all */
    public boolean isEmpty() {
        return chunksByHolder.isEmpty();
    }

    private static List<ChunkPosition> chunksOf(Cuboid region) {
        Objects.requireNonNull(region, "region");
        List<ChunkPosition> chunks = new ArrayList<>();
        for (int x = region.minChunkX(); x <= region.maxChunkX(); x++) {
            for (int z = region.minChunkZ(); z <= region.maxChunkZ(); z++) {
                chunks.add(new ChunkPosition(region.world(), x, z));
            }
        }
        return chunks;
    }
}
