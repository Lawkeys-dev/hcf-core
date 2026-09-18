package com.lawkeys.hcfcore.events.king;

import java.util.Map;
import java.util.UUID;

/**
 * Persistence contract for the items a King is owed back.
 *
 * <p>The contents are opaque bytes here: turning items into bytes is the server's
 * business, and keeping it out of this contract is what keeps the stash testable.
 *
 * <p><strong>Threading.</strong> Blocking; async task only (CONTRIBUTING.md section 5).
 */
public interface KingStashStore {

    void initSchema() throws Exception;

    /** @return every stash still owed, keyed by player */
    Map<UUID, byte[]> loadAll() throws Exception;

    void save(UUID playerId, byte[] contents) throws Exception;

    void delete(UUID playerId) throws Exception;

    KingStashStore NO_OP = new KingStashStore() {
        @Override
        public void initSchema() {
        }

        @Override
        public Map<UUID, byte[]> loadAll() {
            return Map.of();
        }

        @Override
        public void save(UUID playerId, byte[] contents) {
        }

        @Override
        public void delete(UUID playerId) {
        }
    };
}
