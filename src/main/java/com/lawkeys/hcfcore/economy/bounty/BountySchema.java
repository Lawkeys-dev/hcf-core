package com.lawkeys.hcfcore.economy.bounty;

import com.lawkeys.hcfcore.database.migration.Migration;

import java.util.List;

/** The bounties' versioned schema: one row per player with a price on their head. */
public final class BountySchema {

    public static final String MODULE = "bounties";

    private BountySchema() {
    }

    private static final Migration V1 = Migration.of(MODULE, 1,
            "bounties",
            List.of("""
                    CREATE TABLE IF NOT EXISTS hcf_bounties (
                        target_uuid VARCHAR(36) NOT NULL PRIMARY KEY,
                        amount DOUBLE NOT NULL
                    )"""));

    public static List<Migration> migrations() {
        return List.of(V1);
    }
}
