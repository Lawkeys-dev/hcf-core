package com.lawkeys.hcfcore.settings;

import com.lawkeys.hcfcore.database.migration.Migration;

import java.util.List;

/** The player settings module's versioned schema. */
public final class SettingsSchema {

    public static final String MODULE = "settings";

    private SettingsSchema() {
    }

    /**
     * One row per player who has switched something off, listing what: everything
     * is on by default, so a player who never touched {@code /settings} costs nothing.
     */
    private static final Migration V1 = Migration.of(MODULE, 1,
            "player settings",
            List.of("""
                    CREATE TABLE IF NOT EXISTS hcf_player_settings (
                        player_uuid VARCHAR(36) NOT NULL PRIMARY KEY,
                        disabled VARCHAR(255) NOT NULL
                    )"""));

    public static List<Migration> migrations() {
        return List.of(V1);
    }
}
