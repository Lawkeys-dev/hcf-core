package com.lawkeys.hcfcore.staff;

import java.util.Map;
import java.util.UUID;

/**
 * Persistence contract for moderation bans.
 *
 * <p><strong>Threading.</strong> Blocking; async task only (CONTRIBUTING.md section 5).
 */
public interface StaffBanStore {

    void initSchema() throws Exception;

    Map<UUID, StaffBan> loadAll() throws Exception;

    void save(StaffBan ban) throws Exception;

    void delete(UUID playerId) throws Exception;

    StaffBanStore NO_OP = new StaffBanStore() {
        @Override
        public void initSchema() {
        }

        @Override
        public Map<UUID, StaffBan> loadAll() {
            return Map.of();
        }

        @Override
        public void save(StaffBan ban) {
        }

        @Override
        public void delete(UUID playerId) {
        }
    };
}
