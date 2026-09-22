package com.lawkeys.hcfcore.limiter;

import com.lawkeys.hcfcore.util.ChunkPosition;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * The limited blocks standing in each team's part of each claimed chunk
 * ({@link ClaimCell}), so that a territory's total is a sum and never a scan.
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
    private final Map<ClaimCell, Map<String, Integer>> counts = new ConcurrentHashMap<>();
    /** Chunks being recounted, and what changed in each team's part of them since the snapshot. */
    private final Map<ChunkPosition, Map<ClaimCell, Map<String, Integer>>> pending = new ConcurrentHashMap<>();
    private final Set<ClaimCell> dirty = ConcurrentHashMap.newKeySet();

    public ClaimBlockCounts(ClaimBlockCountStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    public int count(ClaimCell cell, String material) {
        return counts.getOrDefault(cell, Map.of()).getOrDefault(material, 0);
    }

    /** @return how many of this block stand in all these cells together */
    public int total(Collection<ClaimCell> cells, String material) {
        int total = 0;
        for (ClaimCell cell : cells) {
            total += count(cell, material);
        }
        return total;
    }

    /** One placed ({@code +1}) or gone ({@code -1}); never below zero. */
    public void adjust(ClaimCell cell, String material, int delta) {
        counts.compute(cell, (at, current) -> {
            Map<String, Integer> next = new HashMap<>(current == null ? Map.of() : current);
            int value = Math.max(0, next.getOrDefault(material, 0) + delta);
            if (value == 0) {
                next.remove(material);
            } else {
                next.put(material, value);
            }
            return next.isEmpty() ? null : Map.copyOf(next);
        });
        pending.computeIfPresent(cell.chunk(), (at, deltas) -> {
            Map<ClaimCell, Map<String, Integer>> next = new HashMap<>(deltas);
            Map<String, Integer> forCell = new HashMap<>(next.getOrDefault(cell, Map.of()));
            forCell.merge(material, delta, Integer::sum);
            next.put(cell, Map.copyOf(forCell));
            return Map.copyOf(next);
        });
        dirty.add(cell);
    }

    /** @return {@code false} when this chunk is already being recounted */
    public boolean beginRecount(ChunkPosition chunk) {
        return pending.putIfAbsent(chunk, Map.of()) == null;
    }

    public boolean isRecounting(ChunkPosition chunk) {
        return pending.containsKey(chunk);
    }

    /**
     * @param exact what the recount found in the snapshot it read, by team; what
     *              changed since is added to it. Every team's part of the chunk is
     *              replaced - a team no longer found there is forgotten
     */
    public void finishRecount(ChunkPosition chunk, Map<UUID, Map<String, Integer>> exact) {
        Map<ClaimCell, Map<String, Integer>> since = pending.remove(chunk);
        Map<ClaimCell, Map<String, Integer>> result = new HashMap<>();
        exact.forEach((team, found) -> result.put(new ClaimCell(chunk, team), new HashMap<>(found)));
        if (since != null) {
            since.forEach((cell, deltas) -> {
                Map<String, Integer> forCell = result.computeIfAbsent(cell, ignored -> new HashMap<>());
                deltas.forEach((material, delta) -> forCell.merge(material, delta, Integer::sum));
            });
        }
        for (ClaimCell cell : Set.copyOf(counts.keySet())) {
            if (cell.chunk().equals(chunk) && !result.containsKey(cell)) {
                counts.remove(cell);
                dirty.add(cell);
            }
        }
        result.forEach((cell, found) -> {
            found.values().removeIf(value -> value <= 0);
            if (found.isEmpty()) {
                counts.remove(cell);
            } else {
                counts.put(cell, Map.copyOf(found));
            }
            dirty.add(cell);
        });
    }

    /** A recount that will never land - the plugin stopping under it. */
    public void abandonRecount(ChunkPosition chunk) {
        pending.remove(chunk);
    }

    /**
     * Forgets the cells a team no longer owns; their stored counts are deleted at the
     * next flush.
     *
     * @return the chunks forgotten - the caller must count them again if they are
     *         claimed again, since what stands in them was not forgotten with them
     */
    public Set<ChunkPosition> prune(Predicate<ClaimCell> stillCounted) {
        Set<ChunkPosition> forgotten = new java.util.HashSet<>();
        for (ClaimCell cell : Set.copyOf(counts.keySet())) {
            if (!stillCounted.test(cell)) {
                counts.remove(cell);
                dirty.add(cell);
                forgotten.add(cell.chunk());
            }
        }
        return forgotten;
    }

    public int cellCount() {
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
        store.loadAll().forEach((cell, theirs) -> {
            Map<String, Integer> kept = new HashMap<>(theirs);
            kept.values().removeIf(value -> value == null || value <= 0);
            if (!kept.isEmpty()) {
                counts.put(cell, Map.copyOf(kept));
            }
        });
    }

    /**
     * Writes the cells whose counts changed. Each mark is cleared before its cell is
     * read and put back if the write fails, so a change made during the write is
     * written next time rather than lost with the mark.
     */
    public int flush() throws Exception {
        int written = 0;
        for (ClaimCell cell : Set.copyOf(dirty)) {
            dirty.remove(cell);
            try {
                store.save(cell, counts.getOrDefault(cell, Map.of()));
                written++;
            } catch (Exception e) {
                dirty.add(cell);
                throw e;
            }
        }
        return written;
    }
}
