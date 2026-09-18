package com.lawkeys.hcfcore.settings;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Persistence contract for player settings.
 *
 * <p>Only what a player has switched <em>off</em> is stored - everything is on until
 * then - so a player who never opened {@code /settings} has no row at all.
 *
 * <p><strong>Threading.</strong> Blocking; async task only (CONTRIBUTING.md section 5).
 */
public interface PlayerSettingsStore {

    void initSchema() throws Exception;

    /** @return every player with something switched off, and what */
    Map<UUID, Set<PlayerSetting>> loadAll() throws Exception;

    /** Replaces what this player has switched off; an empty set removes their row. */
    void save(UUID playerId, Set<PlayerSetting> disabled) throws Exception;

    PlayerSettingsStore NO_OP = new PlayerSettingsStore() {
        @Override
        public void initSchema() {
        }

        @Override
        public Map<UUID, Set<PlayerSetting>> loadAll() {
            return Map.of();
        }

        @Override
        public void save(UUID playerId, Set<PlayerSetting> disabled) {
        }
    };
}
