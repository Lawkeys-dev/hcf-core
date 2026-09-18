package com.lawkeys.hcfcore.kit;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persistence contract for kits and the waits between taking them.
 *
 * <p><strong>Threading.</strong> Blocking; async task only (CONTRIBUTING.md section 5).
 */
public interface KitStore {

    void initSchema() throws Exception;

    Collection<Kit> loadKits() throws Exception;

    /** @return every player's waits, keyed by player then by kit id */
    Map<UUID, Map<String, Long>> loadCooldowns() throws Exception;

    void saveKit(Kit kit) throws Exception;

    void deleteKit(String id) throws Exception;

    /** Replaces this player's waits with exactly this set. */
    void saveCooldowns(UUID playerId, Map<String, Long> cooldowns) throws Exception;

    /** @return every player's kit layouts, keyed by player then by kit id, in {@link KitLayout#format} form */
    Map<UUID, Map<String, String>> loadLayouts() throws Exception;

    /** Replaces this player's layouts with exactly this set. */
    void saveLayouts(UUID playerId, Map<String, String> layouts) throws Exception;

    KitStore NO_OP = new KitStore() {
        @Override
        public void initSchema() {
        }

        @Override
        public Collection<Kit> loadKits() {
            return List.of();
        }

        @Override
        public Map<UUID, Map<String, Long>> loadCooldowns() {
            return Map.of();
        }

        @Override
        public void saveKit(Kit kit) {
        }

        @Override
        public void deleteKit(String id) {
        }

        @Override
        public void saveCooldowns(UUID playerId, Map<String, Long> cooldowns) {
        }

        @Override
        public Map<UUID, Map<String, String>> loadLayouts() {
            return Map.of();
        }

        @Override
        public void saveLayouts(UUID playerId, Map<String, String> layouts) {
        }
    };
}
