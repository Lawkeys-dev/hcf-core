package com.lawkeys.hcfcore.claim;

import com.lawkeys.hcfcore.database.migration.Migration;

import java.util.List;

/**
 * The claim module's versioned schema.
 *
 * <p>Versioned independently of {@code team} (ARCHITECTURE.md section 4): the
 * migrator tracks one version per module, so this module can evolve without
 * touching the team tables.
 *
 * <p>The primary key on {@code (world, chunk_x, chunk_z)} is what makes
 * over-claiming impossible at the storage level too, not only in the manager:
 * two teams can never hold rows for the same chunk (FEATURES.md section 3).
 *
 * <p>Statements stay portable across MySQL and SQLite. Never edit a released
 * migration - append the next version.
 */
public final class ClaimSchema {

    public static final String MODULE = "claim";

    private ClaimSchema() {
    }

    private static final Migration V1 = Migration.of(MODULE, 1,
            "team claims and homes",
            List.of(
                    """
                    CREATE TABLE IF NOT EXISTS hcf_team_claims (
                        world VARCHAR(64) NOT NULL,
                        chunk_x INT NOT NULL,
                        chunk_z INT NOT NULL,
                        team_id VARCHAR(36) NOT NULL,
                        claimed_at BIGINT NOT NULL,
                        PRIMARY KEY (world, chunk_x, chunk_z)
                    )""",
                    """
                    CREATE TABLE IF NOT EXISTS hcf_team_homes (
                        team_id VARCHAR(36) NOT NULL,
                        home_type VARCHAR(16) NOT NULL,
                        world VARCHAR(64) NOT NULL,
                        x DOUBLE NOT NULL,
                        y DOUBLE NOT NULL,
                        z DOUBLE NOT NULL,
                        yaw REAL NOT NULL,
                        pitch REAL NOT NULL,
                        set_at BIGINT NOT NULL,
                        PRIMARY KEY (team_id, home_type)
                    )"""));

    public static List<Migration> migrations() {
        return List.of(V1);
    }
}
