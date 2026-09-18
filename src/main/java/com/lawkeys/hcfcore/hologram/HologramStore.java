package com.lawkeys.hcfcore.hologram;

import java.util.Collection;
import java.util.List;

/**
 * Persistence contract for holograms.
 *
 * <p><strong>Threading.</strong> Blocking; async task only (CONTRIBUTING.md section 5).
 */
public interface HologramStore {

    void initSchema() throws Exception;

    Collection<Hologram> loadAll() throws Exception;

    void save(Hologram hologram) throws Exception;

    void delete(String id) throws Exception;

    HologramStore NO_OP = new HologramStore() {
        @Override
        public void initSchema() {
        }

        @Override
        public Collection<Hologram> loadAll() {
            return List.of();
        }

        @Override
        public void save(Hologram hologram) {
        }

        @Override
        public void delete(String id) {
        }
    };
}
