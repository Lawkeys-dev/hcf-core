package com.lawkeys.hcfcore.lives;

import com.lawkeys.hcfcore.database.migration.Migration;

import java.util.List;

/** The lives module's versioned schema. */
public final class LivesSchema {

    public static final String MODULE = "lives";

    private LivesSchema() {
    }

    /** One row per player whose lives have ever changed; the rest hold the starting amount. */
    private static final Migration V1 = Migration.of(MODULE, 1,
            "lives",
            List.of("""
                    CREATE TABLE IF NOT EXISTS hcf_player_lives (
                        player_uuid VARCHAR(36) NOT NULL PRIMARY KEY,
                        lives INT NOT NULL
                    )"""));

    public static List<Migration> migrations() {
        return List.of(V1);
    }
}
