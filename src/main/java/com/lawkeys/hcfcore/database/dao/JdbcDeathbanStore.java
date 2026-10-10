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
        saveAll(List.of(deathban));
    }

    @Override
    public void saveAll(Collection<Deathban> deathbans) throws SQLException {
        if (deathbans.isEmpty()) {
            return;
        }
        List<Deathban> list = deathbans instanceof List<Deathban> l ? l : new ArrayList<>(deathbans);
        Transactions.run(dataSource, connection -> {
            int[] updateCounts;
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE hcf_deathbans SET expires_at = ?, reason = ? WHERE player_uuid = ?")) {
                for (Deathban ban : list) {
                    update.setLong(1, ban.expiresAt());
                    update.setString(2, ban.reason());
                    update.setString(3, ban.playerId().toString());
                    update.addBatch();
                }
                updateCounts = update.executeBatch();
            }

            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO hcf_deathbans (expires_at, reason, player_uuid) VALUES (?, ?, ?)")) {
                boolean hasInserts = false;
                for (int i = 0; i < list.size(); i++) {
                    if (updateCounts[i] == 0) {
                        Deathban ban = list.get(i);
                        insert.setLong(1, ban.expiresAt());
                        insert.setString(2, ban.reason());
                        insert.setString(3, ban.playerId().toString());
                        insert.addBatch();
                        hasInserts = true;
                    }
                }
                if (hasInserts) {
                    insert.executeBatch();
                }
            }
        });
    }

    @Override
    public void delete(UUID playerId) throws SQLException {
        deleteAll(List.of(playerId));
    }

    @Override
    public void deleteAll(Collection<UUID> playerIds) throws SQLException {
        if (playerIds.isEmpty()) {
            return;
        }
        Transactions.run(dataSource, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM hcf_deathbans WHERE player_uuid = ?")) {
                for (UUID playerId : playerIds) {
                    statement.setString(1, playerId.toString());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        });
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
