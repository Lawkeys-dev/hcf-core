package com.lawkeys.hcfcore.staff;

import java.util.List;
import java.util.UUID;

/**
 * The archive of inventories captured at death.
 *
 * <p><strong>This one is not cached, and that is a deliberate break from every
 * other store in the project.</strong> The rule elsewhere is that the memory cache
 * is the source of truth (ARCHITECTURE.md section 3), because the data is live
 * state read on a hot path. This is not live state: it is an archive, read only
 * when a staff member asks about one player. Caching it would mean holding
 * {@code keep} full inventories for every player who has ever died - three
 * inventories each for a thousand players is hundreds of megabytes of heap to
 * serve a command used a few times a day. So it is queried on demand, off the main
 * thread, and nothing is held.
 *
 * <p><strong>Threading.</strong> Blocking; async task only (CONTRIBUTING.md section 5).
 */
public interface LastInventoryStore {

    void initSchema() throws Exception;

    /**
     * Records a death and drops anything older than the newest {@code keep}.
     *
     * @param keep how many to keep for this player, at least 1
     */
    void save(DeathSnapshot snapshot, int keep) throws Exception;

    /** @return this player's deaths, newest first, at most {@code limit} of them */
    List<DeathSnapshot> recent(UUID playerId, int limit) throws Exception;

    LastInventoryStore NO_OP = new LastInventoryStore() {
        @Override
        public void initSchema() {
        }

        @Override
        public void save(DeathSnapshot snapshot, int keep) {
        }

        @Override
        public List<DeathSnapshot> recent(UUID playerId, int limit) {
            return List.of();
        }
    };
}
