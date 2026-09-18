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

    public static List<Migration> migrations() {
        return List.of(V1);
    }
}
