package com.lawkeys.hcfcore.database.dao;

import com.lawkeys.hcfcore.database.migration.SchemaMigrator;
import com.lawkeys.hcfcore.limiter.ClaimBlockCountStore;
import com.lawkeys.hcfcore.limiter.ClaimCell;
import com.lawkeys.hcfcore.limiter.LimiterSchema;
import com.lawkeys.hcfcore.util.ChunkPosition;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * JDBC implementation of {@link ClaimBlockCountStore}, valid on MySQL and SQLite alike.
 *
 * <p><strong>Threading.</strong> Blocking; async task only (CONTRIBUTING.md section 5).
 */
public final class JdbcClaimBlockCountStore implements ClaimBlockCountStore {

    private final DataSource dataSource;
    private final Consumer<String> logger;

    public JdbcClaimBlockCountStore(DataSource dataSource, Consumer<String> logger) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void initSchema() throws SQLException {
        new SchemaMigrator(dataSource, logger).migrate(LimiterSchema.migrations());
    }

    @Override
    public Map<ClaimCell, Map<String, Integer>> loadAll() throws SQLException {
        Map<ClaimCell, Map<String, Integer>> all = new HashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT world, chunk_x, chunk_z, team_id, material, amount FROM hcf_claim_team_block_counts");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                ClaimCell cell = new ClaimCell(
                        new ChunkPosition(rs.getString("world"), rs.getInt("chunk_x"), rs.getInt("chunk_z")),
                        java.util.UUID.fromString(rs.getString("team_id")));
                all.computeIfAbsent(cell, key -> new HashMap<>()).put(rs.getString("material"), rs.getInt("amount"));
            }
        }
        return all;
    }

    /** Delete-then-insert in one transaction: a cell holds a handful of rows. */
    @Override
    public void save(ClaimCell cell, Map<String, Integer> counts) throws SQLException {
        ChunkPosition chunk = cell.chunk();
        Transactions.run(dataSource, connection -> {
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM hcf_claim_team_block_counts WHERE world = ? AND chunk_x = ? AND chunk_z = ? "
                            + "AND team_id = ?")) {
                delete.setString(1, chunk.world());
                delete.setInt(2, chunk.x());
                delete.setInt(3, chunk.z());
                delete.setString(4, cell.teamId().toString());
                delete.executeUpdate();
            }
            if (!counts.isEmpty()) {
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO hcf_claim_team_block_counts (world, chunk_x, chunk_z, team_id, material, amount) "
                                + "VALUES (?, ?, ?, ?, ?, ?)")) {
                    for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                        insert.setString(1, chunk.world());
                        insert.setInt(2, chunk.x());
                        insert.setInt(3, chunk.z());
                        insert.setString(4, cell.teamId().toString());
                        insert.setString(5, entry.getKey());
                        insert.setInt(6, entry.getValue());
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
            }
        });
    }
}
