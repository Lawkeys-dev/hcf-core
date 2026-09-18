package com.lawkeys.hcfcore.stats;

import java.util.Collection;
import java.util.List;

/**
 * Persistence contract for player statistics.
 *
 * <p><strong>Threading.</strong> Blocking; async task only (CONTRIBUTING.md section 5).
 */
public interface StatsStore {

    void initSchema() throws Exception;

    Collection<PlayerStats> loadAll() throws Exception;

    void save(PlayerStats stats) throws Exception;

    StatsStore NO_OP = new StatsStore() {
        @Override
        public void initSchema() {
        }

        @Override
        public Collection<PlayerStats> loadAll() {
            return List.of();
        }

        @Override
        public void save(PlayerStats stats) {
        }
    };
}
