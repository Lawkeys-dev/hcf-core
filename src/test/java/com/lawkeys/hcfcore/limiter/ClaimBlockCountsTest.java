package com.lawkeys.hcfcore.limiter;

import com.lawkeys.hcfcore.util.ChunkPosition;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Block limits per claim: the counts, and the rule, without a server. */
class ClaimBlockCountsTest {

    private static final ChunkPosition A = new ChunkPosition("world", 0, 0);
    private static final ChunkPosition B = new ChunkPosition("world", 1, 0);

    @Test
    void aTerritoryIsTheSumOfItsChunks() {
        ClaimBlockCounts counts = new ClaimBlockCounts(ClaimBlockCountStore.NO_OP);
        counts.adjust(A, "HOPPER", 1);
        counts.adjust(A, "HOPPER", 1);
        counts.adjust(B, "HOPPER", 1);
        counts.adjust(B, "SPAWNER", 1);
        assertEquals(3, counts.total(List.of(A, B), "HOPPER"));
        assertEquals(2, counts.total(List.of(A), "HOPPER"));
        assertEquals(1, counts.total(List.of(A, B), "SPAWNER"));
    }

    @Test
    void aCountNeverGoesBelowZero() {
        ClaimBlockCounts counts = new ClaimBlockCounts(ClaimBlockCountStore.NO_OP);
        counts.adjust(A, "HOPPER", -1);
        assertEquals(0, counts.count(A, "HOPPER"));
        counts.adjust(A, "HOPPER", 1);
        assertEquals(1, counts.count(A, "HOPPER"), "a break the count never saw does not make a debt");
    }

    /** A recount replaces the chunk with what it found - drift from water, physics, anything - put right. */
    @Test
    void aRecountMakesAChunkExact() {
        ClaimBlockCounts counts = new ClaimBlockCounts(ClaimBlockCountStore.NO_OP);
        counts.adjust(A, "HOPPER", 1);
        counts.adjust(A, "REDSTONE_WIRE", 1);
        assertTrue(counts.beginRecount(A));
        counts.finishRecount(A, Map.of("HOPPER", 5));
        assertEquals(5, counts.count(A, "HOPPER"));
        assertEquals(0, counts.count(A, "REDSTONE_WIRE"), "washed away, and the recount saw it");
    }

    /**
     * The snapshot a recount reads is older than its result: what was placed and
     * broken in between is added, so a recount never undoes a placement it did not see.
     */
    @Test
    void whatChangesDuringARecountIsKept() {
        ClaimBlockCounts counts = new ClaimBlockCounts(ClaimBlockCountStore.NO_OP);
        assertTrue(counts.beginRecount(A));
        assertFalse(counts.beginRecount(A), "one recount of a chunk at a time");
        counts.adjust(A, "HOPPER", 1);
        counts.adjust(A, "HOPPER", 1);
        counts.adjust(A, "SPAWNER", -1);
        counts.finishRecount(A, Map.of("HOPPER", 10, "SPAWNER", 3));
        assertEquals(12, counts.count(A, "HOPPER"));
        assertEquals(2, counts.count(A, "SPAWNER"));
        assertFalse(counts.isRecounting(A));

        counts.adjust(A, "HOPPER", 1);
        counts.beginRecount(A);
        counts.finishRecount(A, Map.of("HOPPER", 13));
        assertEquals(13, counts.count(A, "HOPPER"), "a change made before the recount began is already in its snapshot");
    }

    @Test
    void chunksNoLongerClaimedAreForgottenAndDeleted() throws Exception {
        Recording store = new Recording();
        ClaimBlockCounts counts = new ClaimBlockCounts(store);
        counts.adjust(A, "HOPPER", 1);
        counts.adjust(B, "HOPPER", 1);
        counts.flush();
        assertEquals(Set.of(B), counts.prune(chunk -> chunk.equals(A)),
                "the caller is told which, to count them again if they are claimed again");
        assertEquals(0, counts.count(B, "HOPPER"));
        counts.flush();
        assertEquals(Map.of(A, Map.of("HOPPER", 1)), store.rows, "B's rows are gone");
    }

    @Test
    void countsSurviveARestartAndAFailedWriteIsRetried() throws Exception {
        Recording store = new Recording();
        ClaimBlockCounts counts = new ClaimBlockCounts(store);
        counts.adjust(A, "HOPPER", 1);
        store.fail = true;
        assertThrows(IllegalStateException.class, counts::flush);
        store.fail = false;
        counts.flush();

        ClaimBlockCounts restarted = new ClaimBlockCounts(store);
        restarted.loadAll();
        assertEquals(1, restarted.count(A, "HOPPER"));
    }

    /** A change landing while its chunk is being written is written by the next flush. */
    @Test
    void aChangeDuringAWriteIsNotLost() throws Exception {
        Recording store = new Recording();
        ClaimBlockCounts counts = new ClaimBlockCounts(store);
        counts.adjust(A, "HOPPER", 1);
        store.duringSave = () -> {
            store.duringSave = () -> { };
            counts.adjust(A, "HOPPER", 1);
        };
        counts.flush();
        assertEquals(Map.of("HOPPER", 1), store.rows.get(A));
        counts.flush();
        assertEquals(Map.of("HOPPER", 2), store.rows.get(A));
    }

    @Test
    void theLimitRule() {
        ClaimBlockLimits limits = new ClaimBlockLimits(true, Map.of("hopper", 64, "spawner", 0));
        assertEquals(64, limits.limitFor("HOPPER").orElseThrow(), "names are read as the server spells them");
        assertTrue(limits.isLimited("SPAWNER"));
        assertFalse(limits.isLimited("CHEST"));
        assertTrue(ClaimBlockLimits.mayPlace(63, 64));
        assertFalse(ClaimBlockLimits.mayPlace(64, 64));
        assertFalse(ClaimBlockLimits.mayPlace(0, 0), "a limit of 0 forbids the block");
        assertFalse(new ClaimBlockLimits(false, Map.of("HOPPER", 1)).isActive(), "switched off, nothing is limited");
        assertFalse(ClaimBlockLimits.none().isActive(), "shipped empty");
        assertEquals(Set.of("HOPPER"), new ClaimBlockLimits(true, Map.of("HOPPER", 1, "BAD", -1)).limits().keySet(),
                "a negative limit is dropped");
    }

    private static final class Recording implements ClaimBlockCountStore {
        final Map<ChunkPosition, Map<String, Integer>> rows = new HashMap<>();
        boolean fail;
        Runnable duringSave = () -> { };

        @Override
        public void initSchema() {
        }

        @Override
        public Map<ChunkPosition, Map<String, Integer>> loadAll() {
            return Map.copyOf(rows);
        }

        @Override
        public void save(ChunkPosition chunk, Map<String, Integer> counts) {
            if (fail) {
                throw new IllegalStateException("database down");
            }
            if (counts.isEmpty()) {
                rows.remove(chunk);
            } else {
                rows.put(chunk, Map.copyOf(counts));
            }
            duringSave.run();
        }
    }
}
