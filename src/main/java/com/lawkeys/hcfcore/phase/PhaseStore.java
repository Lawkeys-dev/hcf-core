package com.lawkeys.hcfcore.phase;

import java.util.Set;
import java.util.UUID;

/**
 * Persistence contract for the map's phase.
 *
 * <p><strong>Threading.</strong> Blocking; async task only (CONTRIBUTING.md section 5).
 */
public interface PhaseStore {

    void initSchema() throws Exception;

    Snapshot load() throws Exception;

    /** Replaces the stored state and the set of players who enabled PvP during SOTW. */
    void save(PhaseState state, Set<UUID> sotwPvpEnabled) throws Exception;

    record Snapshot(PhaseState state, Set<UUID> sotwPvpEnabled) {

        public Snapshot {
            java.util.Objects.requireNonNull(state, "state");
            sotwPvpEnabled = Set.copyOf(sotwPvpEnabled);
        }
    }

    PhaseStore NO_OP = new PhaseStore() {
        @Override
        public void initSchema() {
        }

        @Override
        public Snapshot load() {
            return new Snapshot(PhaseState.NONE, Set.of());
        }

        @Override
        public void save(PhaseState state, Set<UUID> sotwPvpEnabled) {
        }
    };
}
