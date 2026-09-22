package com.lawkeys.hcfcore.limiter;

import com.lawkeys.hcfcore.database.migration.Migration;

import java.util.List;

/** The limiter module's versioned schema: the limited blocks counted in claimed chunks. */
public final class LimiterSchema {

    public static final String MODULE = "limiter";

    private LimiterSchema() {
    }

    private static final Migration V1 = Migration.of(MODULE, 1,
            "limited blocks per claimed chunk",
            List.of("""
                    CREATE TABLE IF NOT EXISTS hcf_claim_block_counts (
                        world VARCHAR(64) NOT NULL,
                        chunk_x INT NOT NULL,
                        chunk_z INT NOT NULL,
                        material VARCHAR(64) NOT NULL,
                        amount INT NOT NULL,
                        PRIMARY KEY (world, chunk_x, chunk_z, material)
                    )"""));

    /**
     * Counted per team as well as per chunk (22/09/2026): claims are block-precise, and
     * one chunk can hold two teams' land. The counts are a cache - every claimed chunk
     * is counted again as it loads - so the chunk-only rows are simply dropped.
     */
    private static final Migration V2 = Migration.of(MODULE, 2,
            "limited blocks per team's part of a chunk",
            List.of("""
                    CREATE TABLE IF NOT EXISTS hcf_claim_team_block_counts (
                        world VARCHAR(64) NOT NULL,
                        chunk_x INT NOT NULL,
                        chunk_z INT NOT NULL,
                        team_id VARCHAR(36) NOT NULL,
                        material VARCHAR(64) NOT NULL,
                        amount INT NOT NULL,
                        PRIMARY KEY (world, chunk_x, chunk_z, team_id, material)
                    )""",
                    "DELETE FROM hcf_claim_block_counts"));

    public static List<Migration> migrations() {
        return List.of(V1, V2);
    }
}
