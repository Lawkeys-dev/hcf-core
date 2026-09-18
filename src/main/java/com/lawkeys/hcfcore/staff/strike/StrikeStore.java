package com.lawkeys.hcfcore.staff.strike;

import java.util.Collection;
import java.util.List;

/**
 * Persistence contract for strikes.
 *
 * <p><strong>Threading.</strong> Blocking; async task only (CONTRIBUTING.md section 5).
 */
public interface StrikeStore {

    void initSchema() throws Exception;

    /** @return every strike ever issued and not pardoned, expired ones included */
    Collection<Strike> loadAll() throws Exception;

    void save(Strike strike) throws Exception;

    void delete(long id) throws Exception;

    StrikeStore NO_OP = new StrikeStore() {
        @Override
        public void initSchema() {
        }

        @Override
        public Collection<Strike> loadAll() {
            return List.of();
        }

        @Override
        public void save(Strike strike) {
        }

        @Override
        public void delete(long id) {
        }
    };
}
