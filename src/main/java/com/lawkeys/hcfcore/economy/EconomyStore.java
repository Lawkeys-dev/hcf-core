package com.lawkeys.hcfcore.economy;

import java.util.Map;
import java.util.UUID;

/**
 * Persistence contract for player balances.
 *
 * <p><strong>Threading.</strong> Blocking; async task only (CONTRIBUTING.md section 5).
 */
public interface EconomyStore {

    void initSchema() throws Exception;

    /** @return every known balance, keyed by player */
    Map<UUID, Double> loadAll() throws Exception;

    void save(UUID playerId, double balance) throws Exception;

    EconomyStore NO_OP = new EconomyStore() {
        @Override
        public void initSchema() {
        }

        @Override
        public Map<UUID, Double> loadAll() {
            return Map.of();
        }

        @Override
        public void save(UUID playerId, double balance) {
        }
    };
}
