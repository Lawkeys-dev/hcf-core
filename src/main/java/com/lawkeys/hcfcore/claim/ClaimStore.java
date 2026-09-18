package com.lawkeys.hcfcore.claim;

import java.util.Collection;
import java.util.UUID;

/**
 * Persistence contract for claims and team homes.
 *
 * <p>Same seam as {@code TeamStore}: {@link ClaimManager} never sees JDBC, which
 * keeps the territory rules unit-testable without a database.
 *
 * <p><strong>Threading.</strong> Every method blocks; only an async task may
 * call it (CONTRIBUTING.md section 5).
 */
public interface ClaimStore {

    void initSchema() throws Exception;

    Collection<Claim> loadClaims() throws Exception;

    Collection<TeamHome> loadHomes() throws Exception;

    /** Replaces the stored claims of {@code teamId} with exactly {@code claims}. */
    void saveClaims(UUID teamId, Collection<Claim> claims) throws Exception;

    void saveHome(TeamHome home) throws Exception;

    void deleteHome(UUID teamId, HomeType type) throws Exception;

    /** Removes every claim and home belonging to a team, on disband. */
    void deleteTeam(UUID teamId) throws Exception;

    ClaimStore NO_OP = new ClaimStore() {
        @Override
        public void initSchema() {
        }

        @Override
        public Collection<Claim> loadClaims() {
            return java.util.List.of();
        }

        @Override
        public Collection<TeamHome> loadHomes() {
            return java.util.List.of();
        }

        @Override
        public void saveClaims(UUID teamId, Collection<Claim> claims) {
        }

        @Override
        public void saveHome(TeamHome home) {
        }

        @Override
        public void deleteHome(UUID teamId, HomeType type) {
        }

        @Override
        public void deleteTeam(UUID teamId) {
        }
    };
}
