package com.lawkeys.hcfcore.database.dao;

import com.lawkeys.hcfcore.database.migration.SchemaMigrator;
import com.lawkeys.hcfcore.lives.LivesSchema;
import com.lawkeys.hcfcore.lives.LivesStore;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/** JDBC implementation of {@link LivesStore}, valid on MySQL and SQLite alike. */
public final class JdbcLivesStore implements LivesStore {

    private final DataSource dataSource;
    private final Consumer<String> logger;

    public JdbcLivesStore(DataSource dataSource, Consumer<String> logger) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void initSchema() throws SQLException {
        new SchemaMigrator(dataSource, logger).migrate(LivesSchema.migrations());
    }

    @Override
    public Map<UUID, Integer> loadAll() throws SQLException {
        Map<UUID, Integer> all = new HashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT player_uuid, lives FROM hcf_player_lives");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                all.put(UUID.fromString(rs.getString("player_uuid")), rs.getInt("lives"));
            }
        }
        return all;
    }

    @Override
    public void save(UUID playerId, int lives) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE hcf_player_lives SET lives = ? WHERE player_uuid = ?")) {
                update.setInt(1, lives);
                update.setString(2, playerId.toString());
                if (update.executeUpdate() > 0) {
                    return;
                }
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO hcf_player_lives (player_uuid, lives) VALUES (?, ?)")) {
                insert.setString(1, playerId.toString());
                insert.setInt(2, lives);
                insert.executeUpdate();
            }
        }
    }
}
