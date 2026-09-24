package com.lawkeys.hcfcore.team;

import com.lawkeys.hcfcore.database.migration.Migration;

import java.util.List;

/**
 * The team module's versioned schema (ARCHITECTURE.md section 4).
 *
 * <p>Every statement is portable across MySQL and SQLite. Two consequences worth
 * knowing before adding to this list:
 * <ul>
 *   <li>Uniqueness on {@code name} is declared inline in {@code CREATE TABLE},
 *       because MySQL has no {@code CREATE UNIQUE INDEX IF NOT EXISTS}.</li>
 *   <li>UUIDs are stored as {@code VARCHAR(36)} text: portable, greppable, and
 *       cheap enough at HCF team counts.</li>
 * </ul>
 *
 * <p>Never edit a released migration - append the next version.
 */
public final class TeamSchema {

    public static final String MODULE = "team";

    private TeamSchema() {
    }

    private static final Migration V1 = Migration.of(MODULE, 1,
            "teams, members and alliances",
            List.of(
                    """
                    CREATE TABLE IF NOT EXISTS hcf_teams (
                        id VARCHAR(36) NOT NULL PRIMARY KEY,
                        name VARCHAR(64) NOT NULL UNIQUE,
                        type VARCHAR(16) NOT NULL,
                        leader_uuid VARCHAR(36) NULL,
                        balance DOUBLE NOT NULL DEFAULT 0,
                        points BIGINT NOT NULL DEFAULT 0,
                        koth_captures INT NOT NULL DEFAULT 0,
                        rally_world VARCHAR(64) NULL,
                        rally_x DOUBLE NULL,
                        rally_y DOUBLE NULL,
                        rally_z DOUBLE NULL,
                        rally_yaw REAL NULL,
                        rally_pitch REAL NULL,
                        rally_expires_at BIGINT NOT NULL DEFAULT 0,
                        created_at BIGINT NOT NULL
                    )""",
                    """
                    CREATE TABLE IF NOT EXISTS hcf_team_members (
                        player_uuid VARCHAR(36) NOT NULL PRIMARY KEY,
                        team_id VARCHAR(36) NOT NULL,
                        role VARCHAR(16) NOT NULL
                    )""",
                    """
                    CREATE TABLE IF NOT EXISTS hcf_team_alliances (
                        team_id VARCHAR(36) NOT NULL,
                        ally_team_id VARCHAR(36) NOT NULL,
                        PRIMARY KEY (team_id, ally_team_id)
                    )"""));

    /**
     * Whether a system team is a safe zone or a combat zone.
     *
     * <p>{@code ALTER TABLE ... ADD COLUMN} is portable to both backends. The
     * system teams stored before it existed were all safe zones by definition, so
     * they are written as {@code SAFE} rather than left for the reader to guess.
     */
    private static final Migration V2 = Migration.of(MODULE, 2,
            "safe or combat zone for system teams",
            List.of(
                    "ALTER TABLE hcf_teams ADD COLUMN system_zone VARCHAR(16) NULL",
                    "UPDATE hcf_teams SET system_zone = 'SAFE' WHERE type = 'SYSTEM'"));

    /**
     * A team's own settings from {@code /team settings}, one row each: a permission
     * ({@code permission.<key>} = the role), its join mode, description or Discord
     * link. A key and a value rather than a column each, so a new setting needs no
     * migration. (The value was 64 characters wide until it held a description, the
     * same day and before any release had it.)
     */
    private static final Migration V3 = Migration.of(MODULE, 3,
            "team settings",
            List.of("""
                    CREATE TABLE IF NOT EXISTS hcf_team_settings (
                        team_id VARCHAR(36) NOT NULL,
                        setting_key VARCHAR(64) NOT NULL,
                        setting_value VARCHAR(255) NOT NULL,
                        PRIMARY KEY (team_id, setting_key)
                    )"""));

    /** Every team migration, in application order. */
    public static List<Migration> migrations() {
        return List.of(V1, V2, V3);
    }
}
