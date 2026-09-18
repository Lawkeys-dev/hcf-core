package com.lawkeys.hcfcore.dtr;

import java.util.Collection;
import java.util.UUID;

/**
 * Persistence contract for DTR state.
 *
 * <p>Same seam as the other modules: {@link DtrManager} never sees JDBC.
 *
 * <p><strong>Threading.</strong> Blocking; async task only (CONTRIBUTING.md section 5).
 */
public interface DtrStore {

    void initSchema() throws Exception;

    Collection<DtrState> loadAll() throws Exception;

    void save(DtrState state) throws Exception;

    void delete(UUID teamId) throws Exception;

    DtrStore NO_OP = new DtrStore() {
        @Override
        public void initSchema() {
        }

        @Override
        public Collection<DtrState> loadAll() {
            return java.util.List.of();
        }

        @Override
        public void save(DtrState state) {
        }

        @Override
        public void delete(UUID teamId) {
        }
    };
}
