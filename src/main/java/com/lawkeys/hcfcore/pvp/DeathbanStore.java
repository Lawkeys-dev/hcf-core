package com.lawkeys.hcfcore.pvp;

import java.util.Collection;
import java.util.UUID;

/**
 * Persistence contract for deathbans.
 *
 * <p><strong>Threading.</strong> Blocking; async task only (CONTRIBUTING.md section 5) -
 * with one documented exception in {@code DeathbanManager}, where a login check
 * must be answered synchronously.
 */
public interface DeathbanStore {

    void initSchema() throws Exception;

    Collection<Deathban> loadActive(long now) throws Exception;

    void save(Deathban deathban) throws Exception;

    void delete(UUID playerId) throws Exception;

    /** Drops rows that expired before {@code now}, so the table does not grow forever. */
    int purgeExpired(long now) throws Exception;

    DeathbanStore NO_OP = new DeathbanStore() {
        @Override
        public void initSchema() {
        }

        @Override
        public Collection<Deathban> loadActive(long now) {
            return java.util.List.of();
        }

        @Override
        public void save(Deathban deathban) {
        }

        @Override
        public void delete(UUID playerId) {
        }

        @Override
        public int purgeExpired(long now) {
            return 0;
        }
    };
}
