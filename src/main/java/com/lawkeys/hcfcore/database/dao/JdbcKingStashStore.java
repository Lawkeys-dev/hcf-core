package com.lawkeys.hcfcore.database.dao;

import com.lawkeys.hcfcore.database.migration.SchemaMigrator;
import com.lawkeys.hcfcore.events.king.KingSchema;
import com.lawkeys.hcfcore.events.king.KingStashStore;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * JDBC implementation of {@link KingStashStore}, valid on MySQL and SQLite alike.
 *
 * <p><strong>Threading.</strong> Blocking; async task only (CONTRIBUTING.md section 5).
 */
public final class JdbcKingStashStore implements KingStashStore {

    private final DataSource dataSource;
    private final Consumer<String> logger;

    public JdbcKingStashStore(DataSource dataSource, Consumer<String> logger) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void initSchema() throws SQLException {
        new SchemaMigrator(dataSource, logger).migrate(KingSchema.migrations());
    }

    @Override
    public Map<UUID, byte[]> loadAll() throws SQLException {
        Map<UUID, byte[]> stashes = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT player_uuid, contents FROM hcf_king_stashes");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                stashes.put(UUID.fromString(rs.getString("player_uuid")), rs.getBytes("contents"));
            }
        }
        return stashes;
    }

    @Override
    public void save(UUID playerId, byte[] contents) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE hcf_king_stashes SET contents = ? WHERE player_uuid = ?")) {
                update.setBytes(1, contents);
                update.setString(2, playerId.toString());
                if (update.executeUpdate() > 0) {
                    return;
                }
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO hcf_king_stashes (contents, player_uuid) VALUES (?, ?)")) {
                insert.setBytes(1, contents);
                insert.setString(2, playerId.toString());
                insert.executeUpdate();
            }
        }
    }

    @Override
    public void delete(UUID playerId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM hcf_king_stashes WHERE player_uuid = ?")) {
            statement.setString(1, playerId.toString());
            statement.executeUpdate();
        }
    }
}
