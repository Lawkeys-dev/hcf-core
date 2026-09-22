package com.lawkeys.hcfcore.limiter;


import java.util.Map;

/**
 * Persistence contract for the limited blocks counted in claimed chunks.
 *
 * <p><strong>Threading.</strong> Blocking; async task only (CONTRIBUTING.md section 5).
 */
public interface ClaimBlockCountStore {

    void initSchema() throws Exception;

    /** @return every cell's counts, material name to how many */
    Map<ClaimCell, Map<String, Integer>> loadAll() throws Exception;

    /** Replaces one cell's counts with exactly these; an empty map deletes them. */
    void save(ClaimCell cell, Map<String, Integer> counts) throws Exception;

    ClaimBlockCountStore NO_OP = new ClaimBlockCountStore() {
        @Override
        public void initSchema() {
        }

        @Override
        public Map<ClaimCell, Map<String, Integer>> loadAll() {
            return Map.of();
        }

        @Override
        public void save(ClaimCell cell, Map<String, Integer> counts) {
        }
    };
}
