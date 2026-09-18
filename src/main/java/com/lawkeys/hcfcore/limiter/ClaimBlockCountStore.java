package com.lawkeys.hcfcore.limiter;

import com.lawkeys.hcfcore.util.ChunkPosition;

import java.util.Map;

/**
 * Persistence contract for the limited blocks counted in claimed chunks.
 *
 * <p><strong>Threading.</strong> Blocking; async task only (CONTRIBUTING.md section 5).
 */
public interface ClaimBlockCountStore {

    void initSchema() throws Exception;

    /** @return every chunk's counts, material name to how many */
    Map<ChunkPosition, Map<String, Integer>> loadAll() throws Exception;

    /** Replaces one chunk's counts with exactly these; an empty map deletes them. */
    void save(ChunkPosition chunk, Map<String, Integer> counts) throws Exception;

    ClaimBlockCountStore NO_OP = new ClaimBlockCountStore() {
        @Override
        public void initSchema() {
        }

        @Override
        public Map<ChunkPosition, Map<String, Integer>> loadAll() {
            return Map.of();
        }

        @Override
        public void save(ChunkPosition chunk, Map<String, Integer> counts) {
        }
    };
}
