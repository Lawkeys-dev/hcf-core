package com.lawkeys.hcfcore.pvp;

import com.lawkeys.hcfcore.database.migration.Migration;

import java.util.List;

/**
 * The PvP module's versioned schema.
 *
 * <p>Only deathbans are persisted. Combat tags are deliberately not: they last
 * tens of seconds, and a tag surviving a restart would punish players for the
 * server's downtime.
 */
public final class PvpSchema {

    public static final String MODULE = "pvp";

    private PvpSchema() {
    }

    private static final Migration V1 = Migration.of(MODULE, 1,
            "player deathbans",
            List.of("""
                    CREATE TABLE IF NOT EXISTS hcf_deathbans (
                        player_uuid VARCHAR(36) NOT NULL PRIMARY KEY,
                        expires_at BIGINT NOT NULL,
                        reason VARCHAR(128) NULL
                    )"""));

    public static List<Migration> migrations() {
        return List.of(V1);
    }
}
