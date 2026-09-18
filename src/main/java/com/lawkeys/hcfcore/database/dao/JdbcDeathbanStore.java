package com.lawkeys.hcfcore.database.dao;

import com.lawkeys.hcfcore.database.migration.SchemaMigrator;
import com.lawkeys.hcfcore.pvp.Deathban;
import com.lawkeys.hcfcore.pvp.DeathbanStore;
import com.lawkeys.hcfcore.pvp.PvpSchema;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * JDBC implementation of {@link DeathbanStore}, valid on MySQL and SQLite alike.
 *
 * <p><strong>Threading.</strong> Blocking; async task only (CONTRIBUTING.md section 5).
 */
public final class JdbcDeathbanStore implements DeathbanStore {

    private final DataSource dataSource;
    private final Consumer<String> logger;

    public JdbcDeathbanStore(DataSource dataSource, Consumer<String> logger) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void initSchema() throws SQLException {
        new SchemaMigrator(dataSource, logger).migrate(PvpSchema.migrations());
    }

    @Override
    public Collection<Deathban> loadActive(long now) throws SQLException {
        List<Deathban> bans = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT player_uuid, expires_at, reason FROM hcf_deathbans WHERE expires_at > ?")) {
            statement.setLong(1, now);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    bans.add(new Deathban(UUID.fromString(rs.getString("player_uuid")),
                            rs.getLong("expires_at"), rs.getString("reason")));
                }
            }
        }
        return bans;
    }

    @Override
    public void save(Deathban deathban) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE hcf_deathbans SET expires_at = ?, reason = ? WHERE player_uuid = ?")) {
                update.setLong(1, deathban.expiresAt());
                update.setString(2, deathban.reason());
                update.setString(3, deathban.playerId().toString());
                if (update.executeUpdate() > 0) {
                    return;
                }
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO hcf_deathbans (expires_at, reason, player_uuid) VALUES (?, ?, ?)")) {
                insert.setLong(1, deathban.expiresAt());
                insert.setString(2, deathban.reason());
                insert.setString(3, deathban.playerId().toString());
                insert.executeUpdate();
            }
        }
    }

    @Override
    public void delete(UUID playerId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM hcf_deathbans WHERE player_uuid = ?")) {
            statement.setString(1, playerId.toString());
            statement.executeUpdate();
        }
    }

    @Override
    public int purgeExpired(long now) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM hcf_deathbans WHERE expires_at <= ?")) {
            statement.setLong(1, now);
            return statement.executeUpdate();
        }
    }
}
