package com.lawkeys.hcfcore.limiter;

import com.lawkeys.hcfcore.util.ChunkPosition;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * The limited blocks standing in each claimed chunk, so that a territory's total is a
 * sum and never a scan.
 *
 * <p>Pure Java. Counting a territory by looking at its blocks would mean loading every
 * chunk of it on the main thread at each placement; so each chunk keeps its own
 * count, stored, kept up by placements, breaks and explosions as they happen, and
 * made exact again by a recount of the chunk - off the main thread - when it loads.
 * What the events cannot see (a redstone wire washed away by water) is put right by
 * the next recount.
 *
 * <p><strong>A recount runs while the game goes on.</strong> Between the snapshot it
 * reads and the moment its result lands, blocks may be placed or broken in that
 * chunk; they are kept aside ({@link #beginRecount}) and added to the result, so a
 * recount never undoes a placement it did not see.
 *
 * <p>Each chunk's map is immutable and replaced whole, so the async save reads a
 * consistent copy.
 */
public final class ClaimBlockCounts {

    private final ClaimBlockCountStore store;
    private final Map<ChunkPosition, Map<String, Integer>> counts = new ConcurrentHashMap<>();
    /** Chunks being recounted, and what changed in them since the snapshot. */
    private final Map<ChunkPosition, Map<String, Integer>> pending = new ConcurrentHashMap<>();
    private final Set<ChunkPosition> dirty = ConcurrentHashMap.newKeySet();

    public ClaimBlockCounts(ClaimBlockCountStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    public int count(ChunkPosition chunk, String material) {
        return counts.getOrDefault(chunk, Map.of()).getOrDefault(material, 0);
    }

    /** @return how many of this block stand in all these chunks together */
    public int total(Collection<ChunkPosition> chunks, String material) {
        int total = 0;
        for (ChunkPosition chunk : chunks) {
            total += count(chunk, material);
        }
        return total;
    }

    /** One placed ({@code +1}) or gone ({@code -1}); never below zero. */
    public void adjust(ChunkPosition chunk, String material, int delta) {
        counts.compute(chunk, (at, current) -> {
            Map<String, Integer> next = new HashMap<>(current == null ? Map.of() : current);
            int value = Math.max(0, next.getOrDefault(material, 0) + delta);
            if (value == 0) {
                next.remove(material);
            } else {
                next.put(material, value);
            }
            return next.isEmpty() ? null : Map.copyOf(next);
        });
        pending.computeIfPresent(chunk, (at, deltas) -> {
            Map<String, Integer> next = new HashMap<>(deltas);
            next.merge(material, delta, Integer::sum);
            return Map.copyOf(next);
        });
        dirty.add(chunk);
    }

    /** @return {@code false} when this chunk is already being recounted */
    public boolean beginRecount(ChunkPosition chunk) {
        return pending.putIfAbsent(chunk, Map.of()) == null;
    }

    public boolean isRecounting(ChunkPosition chunk) {
        return pending.containsKey(chunk);
    }

    /**
     * @param exact what the recount found in the snapshot it read; what changed since
     *              is added to it
     */
    public void finishRecount(ChunkPosition chunk, Map<String, Integer> exact) {
        Map<String, Integer> since = pending.remove(chunk);
        Map<String, Integer> result = new HashMap<>(exact);
        if (since != null) {
            since.forEach((material, delta) -> result.merge(material, delta, Integer::sum));
        }
        result.values().removeIf(value -> value <= 0);
        if (result.isEmpty()) {
            counts.remove(chunk);
        } else {
            counts.put(chunk, Map.copyOf(result));
        }
        dirty.add(chunk);
    }

    /** A recount that will never land - the plugin stopping under it. */
    public void abandonRecount(ChunkPosition chunk) {
        pending.remove(chunk);
    }

    /**
     * Forgets chunks that are no longer claimed; their stored counts are deleted at the
     * next flush.
     *
     * @return the chunks forgotten - the caller must count them again if they are
     *         claimed again, since what stands in them was not forgotten with them
     */
    public Set<ChunkPosition> prune(Predicate<ChunkPosition> stillCounted) {
        Set<ChunkPosition> forgotten = new java.util.HashSet<>();
        for (ChunkPosition chunk : Set.copyOf(counts.keySet())) {
            if (!stillCounted.test(chunk)) {
                counts.remove(chunk);
                dirty.add(chunk);
                forgotten.add(chunk);
            }
        }
        return forgotten;
    }

    public int chunkCount() {
        return counts.size();
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    public void loadAll() throws Exception {
        store.initSchema();
        counts.clear();
        pending.clear();
        dirty.clear();
        store.loadAll().forEach((chunk, theirs) -> {
            Map<String, Integer> kept = new HashMap<>(theirs);
            kept.values().removeIf(value -> value == null || value <= 0);
            if (!kept.isEmpty()) {
                counts.put(chunk, Map.copyOf(kept));
            }
        });
    }

    /**
     * Writes the chunks whose counts changed. Each mark is cleared before its chunk is
     * read and put back if the write fails, so a change made during the write is
     * written next time rather than lost with the mark.
     */
    public int flush() throws Exception {
        int written = 0;
        for (ChunkPosition chunk : Set.copyOf(dirty)) {
            dirty.remove(chunk);
            try {
                store.save(chunk, counts.getOrDefault(chunk, Map.of()));
                written++;
            } catch (Exception e) {
                dirty.add(chunk);
                throw e;
            }
        }
        return written;
    }
}
