package com.lawkeys.hcfcore.economy.bounty;

import java.util.Map;
import java.util.UUID;

/**
 * Persistence contract for bounties.
 *
 * <p><strong>Threading.</strong> Blocking; async task only (CONTRIBUTING.md section 5).
 */
public interface BountyStore {

    void initSchema() throws Exception;

    Map<UUID, Double> loadAll() throws Exception;

    /** @param amount {@code 0} removes the row: the bounty was claimed or cleared */
    void save(UUID target, double amount) throws Exception;

    BountyStore NO_OP = new BountyStore() {
        @Override
        public void initSchema() {
        }

        @Override
        public Map<UUID, Double> loadAll() {
            return Map.of();
        }

        @Override
        public void save(UUID target, double amount) {
        }
    };
}
