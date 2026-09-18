package com.lawkeys.hcfcore.database.dao;

import com.lawkeys.hcfcore.database.migration.SchemaMigrator;
import com.lawkeys.hcfcore.staff.DeathSnapshot;
import com.lawkeys.hcfcore.staff.LastInventoryStore;
import com.lawkeys.hcfcore.staff.StaffSchema;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * JDBC implementation of {@link LastInventoryStore}, valid on MySQL and SQLite alike.
 *
 * <p><strong>Threading.</strong> Blocking; async task only (CONTRIBUTING.md section 5).
 */
public final class JdbcLastInventoryStore implements LastInventoryStore {

    /**
     * Keeps the newest {@code keep} rows for one player and deletes the rest.
     *
     * <p>The derived table is not decoration: MySQL refuses a subquery that reads
     * the same table a DELETE is writing ("You can't specify target table ... for
     * update in FROM clause"), and wrapping the subquery in {@code SELECT ... FROM
     * (...) AS x} materialises it, which MySQL accepts. SQLite accepts the same
     * statement, so one string serves both - the rule this project follows for every
     * DAO.
     */
    private static final String PRUNE = """
            DELETE FROM hcf_staff_last_inventories
             WHERE player_uuid = ?
               AND died_at NOT IN (
                   SELECT died_at FROM (
                       SELECT died_at FROM hcf_staff_last_inventories
                        WHERE player_uuid = ?
                        ORDER BY died_at DESC
                        LIMIT ?
                   ) AS newest
               )""";

    private final DataSource dataSource;
    private final Consumer<String> logger;

    public JdbcLastInventoryStore(DataSource dataSource, Consumer<String> logger) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void initSchema() throws SQLException {
        new SchemaMigrator(dataSource, logger).migrate(StaffSchema.migrations());
    }

    @Override
    public void save(DeathSnapshot snapshot, int keep) throws SQLException {
        int kept = Math.max(1, keep);
        Transactions.run(dataSource, connection -> {
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO hcf_staff_last_inventories (player_uuid, died_at, contents) "
                            + "VALUES (?, ?, ?)")) {
                insert.setString(1, snapshot.playerId().toString());
                insert.setLong(2, snapshot.diedAt());
                insert.setBytes(3, snapshot.contents());
                insert.executeUpdate();
            }
            // In the same transaction as the insert, so a reader never sees the
            // archive briefly holding one more than it should.
            try (PreparedStatement prune = connection.prepareStatement(PRUNE)) {
                prune.setString(1, snapshot.playerId().toString());
                prune.setString(2, snapshot.playerId().toString());
                prune.setInt(3, kept);
                prune.executeUpdate();
            }
        });
    }

    @Override
    public List<DeathSnapshot> recent(UUID playerId, int limit) throws SQLException {
        List<DeathSnapshot> snapshots = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT died_at, contents FROM hcf_staff_last_inventories "
                             + "WHERE player_uuid = ? ORDER BY died_at DESC LIMIT ?")) {
            statement.setString(1, playerId.toString());
            statement.setInt(2, Math.max(1, limit));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    snapshots.add(new DeathSnapshot(playerId, rs.getLong("died_at"),
                            rs.getBytes("contents")));
                }
            }
        }
        return snapshots;
    }
}
