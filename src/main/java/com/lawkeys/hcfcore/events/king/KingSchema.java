package com.lawkeys.hcfcore.events.king;

import com.lawkeys.hcfcore.database.migration.Migration;

import java.util.List;

/**
 * Kill the King's versioned schema: the stashes of items owed back to Kings.
 *
 * <p>{@code MEDIUMBLOB} rather than {@code BLOB}, because a whole inventory - with
 * shulker boxes full of items - can outgrow MySQL's {@code BLOB}, which stops at
 * 65,535 bytes; {@code MEDIUMBLOB} goes to 16 MB (MySQL 8.4 reference manual,
 * "String Type Storage Requirements"). SQLite takes the same declaration: any
 * type containing "BLOB" gets BLOB affinity (sqlite.org, "Datatypes In SQLite",
 * section 3.1, rule 3). Both verified on 11/09/2026.
 */
public final class KingSchema {

    public static final String MODULE = "king";

    private KingSchema() {
    }

    private static final Migration V1 = Migration.of(MODULE, 1,
            "items owed back to Kings",
            List.of("""
                    CREATE TABLE IF NOT EXISTS hcf_king_stashes (
                        player_uuid VARCHAR(36) NOT NULL PRIMARY KEY,
                        contents MEDIUMBLOB NOT NULL
                    )"""));

    public static List<Migration> migrations() {
        return List.of(V1);
    }
}
