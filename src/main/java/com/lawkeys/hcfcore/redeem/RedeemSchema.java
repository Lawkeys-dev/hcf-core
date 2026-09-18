package com.lawkeys.hcfcore.redeem;

import com.lawkeys.hcfcore.database.migration.Migration;

import java.util.List;

/** The redeem module's versioned schema. */
public final class RedeemSchema {

    public static final String MODULE = "redeem";

    private RedeemSchema() {
    }

    /**
     * Codes, and who has used each. The primary key on a redemption is what makes
     * "once per player" hold in the database too, not only in the cache.
     */
    private static final Migration V1 = Migration.of(MODULE, 1,
            "redeem codes and their redemptions",
            List.of("""
                    CREATE TABLE IF NOT EXISTS hcf_redeem_codes (
                        code_key VARCHAR(32) NOT NULL PRIMARY KEY,
                        code VARCHAR(32) NOT NULL,
                        commands TEXT NOT NULL,
                        max_uses INT NOT NULL,
                        created_by VARCHAR(32) NOT NULL,
                        created_at BIGINT NOT NULL
                    )""", """
                    CREATE TABLE IF NOT EXISTS hcf_redeem_uses (
                        code_key VARCHAR(32) NOT NULL,
                        player_uuid VARCHAR(36) NOT NULL,
                        redeemed_at BIGINT NOT NULL,
                        PRIMARY KEY (code_key, player_uuid)
                    )"""));

    public static List<Migration> migrations() {
        return List.of(V1);
    }
}
