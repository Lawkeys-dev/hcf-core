package com.lawkeys.hcfcore.team;

import java.util.Collection;
import java.util.UUID;

/**
 * Persistence contract for teams.
 *
 * <p>Kept as an interface so {@link TeamManager} - the rule engine - never sees
 * JDBC, which is what lets the whole module be unit-tested without a database or
 * a server. The production implementation is
 * {@code com.lawkeys.hcfcore.database.dao.JdbcTeamStore}.
 *
 * <p><strong>Threading.</strong> Every method here blocks on I/O and must only
 * ever be called from an async task (CONTRIBUTING.md section 5: never block the main
 * thread with a DB query). {@link TeamManager#flush()} is the intended caller.
 */
public interface TeamStore {

    /** Creates tables/indexes if needed. Called once during plugin startup, off the main thread. */
    void initSchema() throws Exception;

    /** Loads every persisted team; used to populate the in-memory cache at startup. */
    Collection<Team> loadAll() throws Exception;

    /** Inserts or updates {@code team} and its members and alliances. */
    void save(Team team) throws Exception;

    /** Deletes a team and its dependent rows. */
    void delete(UUID teamId) throws Exception;

    /** A store that keeps nothing, for tests and for running with persistence disabled. */
    TeamStore NO_OP = new TeamStore() {
        @Override
        public void initSchema() {
        }

        @Override
        public Collection<Team> loadAll() {
            return java.util.List.of();
        }

        @Override
        public void save(Team team) {
        }

        @Override
        public void delete(UUID teamId) {
        }
    };
}
