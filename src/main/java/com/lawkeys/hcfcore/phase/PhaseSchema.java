package com.lawkeys.hcfcore.phase;

import com.lawkeys.hcfcore.database.migration.Migration;

import java.util.List;

/**
 * The phase module's versioned schema: one row for the map's phase, and the
 * players who enabled PvP during the current SOTW.
 */
public final class PhaseSchema {

    public static final String MODULE = "phase";

    private PhaseSchema() {
    }

    private static final Migration V1 = Migration.of(MODULE, 1,
            "map phase and SOTW choices",
            List.of("""
                    CREATE TABLE IF NOT EXISTS hcf_map_phase (
                        id INTEGER NOT NULL PRIMARY KEY,
                        sotw_ends_at BIGINT NOT NULL,
                        eotw_since BIGINT NOT NULL,
                        sotw_schedule_handled BIGINT NOT NULL,
                        eotw_schedule_handled BIGINT NOT NULL
                    )""", """
                    CREATE TABLE IF NOT EXISTS hcf_sotw_pvp_enabled (
                        player_uuid VARCHAR(36) NOT NULL PRIMARY KEY
                    )"""));

    /**
     * The Purge: a fifth instant on the same row. {@code ADD COLUMN} with a default,
     * portable to MySQL and SQLite alike, so a database from before it reads as "no
     * Purge ever ran".
     */
    private static final Migration V2 = Migration.of(MODULE, 2,
            "the purge",
            List.of("ALTER TABLE hcf_map_phase ADD COLUMN purge_ends_at BIGINT NOT NULL DEFAULT 0"));

    public static List<Migration> migrations() {
        return List.of(V1, V2);
    }
}
