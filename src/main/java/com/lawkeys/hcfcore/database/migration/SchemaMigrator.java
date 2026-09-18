package com.lawkeys.hcfcore.database.migration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Applies {@link Migration}s and remembers, per module, how far the schema has
 * been brought (ARCHITECTURE.md section 4).
 *
 * <p>Deliberately plain JDBC over a {@link DataSource}: no Bukkit, no HikariCP
 * type in the signature, so it can be exercised in unit tests against an
 * in-memory SQLite database.
 *
 * <p><strong>Threading.</strong> Blocking I/O - callers must already be off the
 * main server thread (CONTRIBUTING.md section 5).
 */
public final class SchemaMigrator {

    private static final String VERSION_TABLE = "hcf_schema_version";

    private final DataSource dataSource;
    private final Consumer<String> logger;

    /** @param logger receives one line per applied migration */
    public SchemaMigrator(DataSource dataSource, Consumer<String> logger) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Applies every migration whose version is above the module's recorded
     * version, in ascending order. Already-applied migrations are skipped, so this
     * is safe to run on every startup.
     *
     * @return the number of migrations applied
     */
    public int migrate(List<Migration> migrations) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            createVersionTable(connection);

            List<Migration> ordered = new ArrayList<>(migrations);
            ordered.sort(Comparator.comparing(Migration::module).thenComparingInt(Migration::version));

            int applied = 0;
            for (Migration migration : ordered) {
                int current = currentVersion(connection, migration.module());
                if (migration.version() <= current) {
                    continue;
                }
                apply(connection, migration);
                applied++;
            }
            return applied;
        }
    }

    /** @return the schema version recorded for {@code module}, or 0 if it has never been migrated. */
    public int currentVersion(String module) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            createVersionTable(connection);
            return currentVersion(connection, module);
        }
    }

    private void createVersionTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + VERSION_TABLE + " ("
                    + "module VARCHAR(64) NOT NULL PRIMARY KEY, "
                    + "version INT NOT NULL, "
                    + "updated_at BIGINT NOT NULL)");
        }
    }

    private int currentVersion(Connection connection, String module) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT version FROM " + VERSION_TABLE + " WHERE module = ?")) {
            statement.setString(1, module);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private void apply(Connection connection, Migration migration) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            try (Statement statement = connection.createStatement()) {
                for (String sql : migration.statements()) {
                    statement.executeUpdate(sql);
                }
            }
            recordVersion(connection, migration);
            connection.commit();
            logger.accept("Applied migration " + migration.module() + " v" + migration.version()
                    + " (" + migration.description() + ")");
        } catch (SQLException e) {
            connection.rollback();
            throw new SQLException("Migration " + migration.module() + " v" + migration.version()
                    + " failed and was rolled back: " + e.getMessage(), e);
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    /**
     * Upserts the module's version. Written as update-then-insert rather than a
     * dialect-specific upsert, since MySQL and SQLite spell that differently.
     */
    private void recordVersion(Connection connection, Migration migration) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE " + VERSION_TABLE + " SET version = ?, updated_at = ? WHERE module = ?")) {
            update.setInt(1, migration.version());
            update.setLong(2, System.currentTimeMillis());
            update.setString(3, migration.module());
            if (update.executeUpdate() > 0) {
                return;
            }
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO " + VERSION_TABLE + " (module, version, updated_at) VALUES (?, ?, ?)")) {
            insert.setString(1, migration.module());
            insert.setInt(2, migration.version());
            insert.setLong(3, System.currentTimeMillis());
            insert.executeUpdate();
        }
    }
}
