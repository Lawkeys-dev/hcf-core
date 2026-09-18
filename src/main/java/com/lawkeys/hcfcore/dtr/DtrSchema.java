package com.lawkeys.hcfcore.dtr;

import com.lawkeys.hcfcore.database.migration.Migration;

import java.util.List;

/**
 * The DTR module's versioned schema.
 *
 * <p>Its own table rather than a column on {@code hcf_teams}: ARCHITECTURE.md
 * section 4 gives each module its own migration counter, and a DTR column on the
 * teams table would have been dead weight for as long as this module did not
 * exist - the "phantom behaviour" section 2 forbids.
 *
 * <p>Two columns, because DTR is derived from them rather than stored directly:
 * the value at the last mutation, and when regeneration resumes.
 */
public final class DtrSchema {

    public static final String MODULE = "dtr";

    private DtrSchema() {
    }

    private static final Migration V1 = Migration.of(MODULE, 1,
            "per-team DTR state",
            List.of("""
                    CREATE TABLE IF NOT EXISTS hcf_team_dtr (
                        team_id VARCHAR(36) NOT NULL PRIMARY KEY,
                        stored_dtr DOUBLE NOT NULL,
                        regen_at BIGINT NOT NULL
                    )"""));

    public static List<Migration> migrations() {
        return List.of(V1);
    }
}
