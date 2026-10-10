package com.lawkeys.hcfcore.staff;

import java.util.Map;
import java.util.UUID;

/**
 * Persistence contract for the survival inventories held during staff mode.
 *
 * <p>The contents are opaque bytes here: turning items into bytes is the server's
 * business, and keeping it out of this contract is what keeps {@link StaffStashes}
 * testable.
 *
 * <p><strong>Threading.</strong> Blocking; async task only (CONTRIBUTING.md section 5).
 */
public interface StaffStashStore {

    void initSchema() throws Exception;

    /** @return every inventory still held, keyed by its owner */
    Map<UUID, byte[]> loadAll() throws Exception;

    void save(UUID playerId, byte[] contents) throws Exception;

    void delete(UUID playerId) throws Exception;

    default void saveAll(Map<UUID, byte[]> stashes) throws Exception {
        for (Map.Entry<UUID, byte[]> entry : stashes.entrySet()) {
            save(entry.getKey(), entry.getValue());
        }
    }

    default void deleteAll(Iterable<UUID> playerIds) throws Exception {
        for (UUID playerId : playerIds) {
            delete(playerId);
        }
    }

    StaffStashStore NO_OP = new StaffStashStore() {
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

        @Override
        public void saveAll(Map<UUID, byte[]> stashes) {
        }

        @Override
        public void deleteAll(Iterable<UUID> playerIds) {
        }
    };
}
