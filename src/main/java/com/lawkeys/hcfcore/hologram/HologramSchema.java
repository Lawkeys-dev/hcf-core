package com.lawkeys.hcfcore.hologram;

import com.lawkeys.hcfcore.database.migration.Migration;

import java.util.List;

/** The hologram module's versioned schema. */
public final class HologramSchema {

    public static final String MODULE = "hologram";

    private HologramSchema() {
    }

    /**
     * One row per hologram; its lines one per line of text, which a chat command
     * cannot break.
     *
     * <p>{@code `lines`} is quoted: LINES is reserved in MySQL, and unquoted this
     * migration was refused by a real MySQL 8.4, keeping the server closed
     * (13/09/2026). SQLite reads the same backticks, and a database that already
     * ran this migration has the same column: nothing to migrate.
     */
    private static final Migration V1 = Migration.of(MODULE, 1,
            "holograms",
            List.of("""
                    CREATE TABLE IF NOT EXISTS hcf_holograms (
                        id VARCHAR(32) NOT NULL PRIMARY KEY,
                        world VARCHAR(64) NOT NULL,
                        x DOUBLE NOT NULL,
                        y DOUBLE NOT NULL,
                        z DOUBLE NOT NULL,
                        `lines` TEXT NOT NULL
                    )"""));

    public static List<Migration> migrations() {
        return List.of(V1);
    }
}
