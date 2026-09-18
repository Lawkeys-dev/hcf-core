package com.lawkeys.hcfcore.kit;

import com.lawkeys.hcfcore.database.migration.Migration;

import java.util.List;

/**
 * The kit module's versioned schema.
 *
 * <p>{@code MEDIUMBLOB} for the contents, for the reason every inventory column in
 * this project uses it: MySQL's {@code BLOB} stops at 65,535 bytes, which a full
 * loadout passes.
 */
public final class KitSchema {

    public static final String MODULE = "kit";

    private KitSchema() {
    }

    private static final Migration V1 = Migration.of(MODULE, 1,
            "kits and the waits between taking them",
            List.of("""
                    CREATE TABLE IF NOT EXISTS hcf_kits (
                        id VARCHAR(32) NOT NULL PRIMARY KEY,
                        display_name VARCHAR(64) NOT NULL,
                        icon VARCHAR(64) NOT NULL,
                        permission VARCHAR(128),
                        cooldown_seconds BIGINT NOT NULL DEFAULT 0,
                        contents MEDIUMBLOB NOT NULL
                    )""",
                    """
                    CREATE TABLE IF NOT EXISTS hcf_kit_cooldowns (
                        player_uuid VARCHAR(36) NOT NULL,
                        kit_id VARCHAR(32) NOT NULL,
                        expires_at BIGINT NOT NULL,
                        PRIMARY KEY (player_uuid, kit_id)
                    )"""));

    /** Version 2: where each player wants each kit's items (the kit layout editor). */
    private static final Migration V2 = Migration.of(MODULE, 2,
            "kit layouts",
            List.of("""
                    CREATE TABLE IF NOT EXISTS hcf_kit_layouts (
                        player_uuid VARCHAR(36) NOT NULL,
                        kit_id VARCHAR(32) NOT NULL,
                        layout VARCHAR(512) NOT NULL,
                        PRIMARY KEY (player_uuid, kit_id)
                    )"""));

    /**
     * Version 3: the icon column goes. Nothing ever read it, and {@code /kit create}
     * always wrote {@code CHEST}. Alone in its migration: MySQL commits on it.
     */
    private static final Migration V3 = Migration.of(MODULE, 3,
            "drop the unused kit icon",
            List.of("ALTER TABLE hcf_kits DROP COLUMN icon"));

    public static List<Migration> migrations() {
        return List.of(V1, V2, V3);
    }
}
