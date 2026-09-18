package com.lawkeys.hcfcore.database.migration;

import java.util.List;

/**
 * One versioned, forward-only schema change owned by a single module.
 *
 * <p>ARCHITECTURE.md section 4 requires versioned migrations so the schema can
 * evolve in production without data loss. Each module ships its own numbered
 * list (see {@code com.lawkeys.hcfcore.team.TeamSchema}) and
 * {@link SchemaMigrator} tracks the applied version per module, so modules can be
 * added and evolve independently of each other.
 *
 * <p>Statements must be portable across MySQL and SQLite, which are the two
 * supported backends: no {@code CREATE INDEX IF NOT EXISTS} (unsupported by
 * MySQL), no {@code AUTO_INCREMENT}/{@code AUTOINCREMENT} (identifiers are UUIDs).
 *
 * <p>A migration's statements run in one transaction, but MySQL commits on every
 * DDL statement: a migration that fails half-way there keeps what ran before the
 * failure, and runs again whole at the next start. A statement that cannot run
 * twice ({@code ALTER TABLE ... ADD COLUMN}, {@code CREATE INDEX}) therefore goes in
 * a migration of its own; {@code MigrationRetryTest} checks it.
 *
 * <p>Never edit a released migration - add the next one.
 */
public interface Migration {

    /** Module identifier, e.g. {@code "team"}. Scopes the version counter. */
    String module();

    /** Version this migration brings the module's schema to; strictly increasing from 1. */
    int version();

    /** Human-readable summary, logged when the migration runs. */
    String description();

    /** Statements to execute, in order, inside a single transaction - which MySQL ends at each DDL statement. */
    List<String> statements();

    static Migration of(String module, int version, String description, List<String> statements) {
        List<String> copy = List.copyOf(statements);
        return new Migration() {
            @Override
            public String module() {
                return module;
            }

            @Override
            public int version() {
                return version;
            }

            @Override
            public String description() {
                return description;
            }

            @Override
            public List<String> statements() {
                return copy;
            }
        };
    }
}
