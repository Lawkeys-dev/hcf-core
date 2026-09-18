package com.lawkeys.hcfcore.lives;

import java.util.Map;
import java.util.UUID;

/**
 * Persistence contract for lives.
 *
 * <p><strong>Threading.</strong> Blocking; async task only (CONTRIBUTING.md section 5).
 */
public interface LivesStore {

    void initSchema() throws Exception;

    Map<UUID, Integer> loadAll() throws Exception;

    void save(UUID playerId, int lives) throws Exception;

    LivesStore NO_OP = new LivesStore() {
        @Override
        public void initSchema() {
        }

        @Override
        public Map<UUID, Integer> loadAll() {
            return Map.of();
        }

        @Override
        public void save(UUID playerId, int lives) {
        }
    };
}
