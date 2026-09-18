package com.lawkeys.hcfcore.stats;

import com.lawkeys.hcfcore.database.migration.Migration;

import java.util.List;

/** The stats module's versioned schema. */
public final class StatsSchema {

    public static final String MODULE = "stats";

    private StatsSchema() {
    }

    /**
     * One row per player the server has ever seen.
     *
     * <p>The name is stored alongside the id so a leaderboard can be rendered
     * without asking the server about players who are offline, which would either
     * block on Mojang or come back empty.
     */
    private static final Migration V1 = Migration.of(MODULE, 1,
            "kills, deaths, killstreaks and playtime",
            List.of("""
                    CREATE TABLE IF NOT EXISTS hcf_player_stats (
                        player_uuid VARCHAR(36) NOT NULL PRIMARY KEY,
                        name VARCHAR(32) NOT NULL,
                        kills INT NOT NULL DEFAULT 0,
                        deaths INT NOT NULL DEFAULT 0,
                        killstreak INT NOT NULL DEFAULT 0,
                        highest_killstreak INT NOT NULL DEFAULT 0,
                        playtime_seconds BIGINT NOT NULL DEFAULT 0,
                        first_seen BIGINT NOT NULL DEFAULT 0,
                        last_seen BIGINT NOT NULL DEFAULT 0
                    )"""));

    public static List<Migration> migrations() {
        return List.of(V1);
    }
}
