package com.lawkeys.hcfcore.database.migration;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every migration can be run again after failing half-way on MySQL.
 *
 * <p>MySQL commits on each DDL statement, so the rollback of a failed migration
 * does not undo the statements before the failure, and its version is not
 * recorded: the next start runs the whole migration again. A statement that
 * fails when run twice ({@code ALTER TABLE ... ADD COLUMN}, {@code CREATE INDEX})
 * is therefore only safe alone in its migration.
 */
class MigrationRetryTest {

    /**
     * Released before the rule, and released migrations are never edited. If its
     * UPDATE ever fails after the column was added, the operator sets
     * {@code hcf_schema_version} for {@code team} to 2 by hand (docs/server/troubleshooting.md).
     */
    private static final Set<String> RELEASED_BEFORE_THE_RULE = Set.of("team v2");

    @Test
    void aStatementThatCannotRunTwiceIsAloneInItsMigration() {
        List<String> found = new ArrayList<>();
        for (Migration migration : MysqlReservedWordsTest.allMigrations()) {
            String where = migration.module() + " v" + migration.version();
            if (migration.statements().size() < 2 || RELEASED_BEFORE_THE_RULE.contains(where)) {
                continue;
            }
            for (String statement : migration.statements()) {
                if (!canRunTwice(statement)) {
                    found.add(where + ": " + statement.strip().lines().findFirst().orElse(""));
                }
            }
        }
        assertTrue(found.isEmpty(), "give each of these its own migration: " + found);
    }

    @Test
    void theRuleTellsTheKindsApart() {
        assertTrue(canRunTwice("CREATE TABLE IF NOT EXISTS hcf_x (a INT)"));
        assertTrue(canRunTwice("DROP TABLE IF EXISTS hcf_x"));
        assertTrue(canRunTwice("UPDATE hcf_x SET a = 1 WHERE b = 2"));
        assertFalse(canRunTwice("ALTER TABLE hcf_x ADD COLUMN c INT"));
        assertFalse(canRunTwice("CREATE INDEX idx ON hcf_x (a)"));
        assertFalse(canRunTwice("CREATE TABLE hcf_x (a INT)"));
        assertFalse(canRunTwice("INSERT INTO hcf_x (a) VALUES (1)"));
        assertEquals(Set.of("team v2"), RELEASED_BEFORE_THE_RULE);
    }

    /** Whether running the statement a second time leaves the same schema and data, without an error. */
    static boolean canRunTwice(String statement) {
        String sql = statement.strip().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        return sql.startsWith("CREATE TABLE IF NOT EXISTS ")
                || sql.startsWith("DROP TABLE IF EXISTS ")
                || sql.startsWith("UPDATE ")
                || sql.startsWith("DELETE ");
    }
}
