package com.lawkeys.hcfcore.database.dao;

import com.lawkeys.hcfcore.database.migration.SchemaMigrator;
import com.lawkeys.hcfcore.stats.PlayerStats;
import com.lawkeys.hcfcore.stats.StatsSchema;
import com.lawkeys.hcfcore.stats.StatsStore;

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
 * JDBC implementation of {@link StatsStore}, valid on MySQL and SQLite alike.
 *
 * <p><strong>Threading.</strong> Blocking; async task only (CONTRIBUTING.md section 5).
 */
public final class JdbcStatsStore implements StatsStore {

    private final DataSource dataSource;
    private final Consumer<String> logger;

    public JdbcStatsStore(DataSource dataSource, Consumer<String> logger) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void initSchema() throws SQLException {
        new SchemaMigrator(dataSource, logger).migrate(StatsSchema.migrations());
    }

    @Override
    public Collection<PlayerStats> loadAll() throws SQLException {
        List<PlayerStats> all = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT player_uuid, name, kills, deaths, killstreak, highest_killstreak, "
                             + "playtime_seconds, first_seen, last_seen FROM hcf_player_stats");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                all.add(PlayerStats.restore(
                        UUID.fromString(rs.getString("player_uuid")),
                        rs.getString("name"),
                        rs.getInt("kills"),
                        rs.getInt("deaths"),
                        rs.getInt("killstreak"),
                        rs.getInt("highest_killstreak"),
                        rs.getLong("playtime_seconds"),
                        rs.getLong("first_seen"),
                        rs.getLong("last_seen")));
            }
        }
        return all;
    }

    @Override
    public void save(PlayerStats stats) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE hcf_player_stats SET name = ?, kills = ?, deaths = ?, killstreak = ?, "
                            + "highest_killstreak = ?, playtime_seconds = ?, first_seen = ?, "
                            + "last_seen = ? WHERE player_uuid = ?")) {
                bind(update, stats);
                update.setString(9, stats.getPlayerId().toString());
                if (update.executeUpdate() > 0) {
                    return;
                }
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO hcf_player_stats (name, kills, deaths, killstreak, "
                            + "highest_killstreak, playtime_seconds, first_seen, last_seen, player_uuid) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                bind(insert, stats);
                insert.setString(9, stats.getPlayerId().toString());
                insert.executeUpdate();
            }
        }
    }

    /**
     * Binds the eight value columns, in the order both statements above declare them.
     *
     * <p>The stored playtime is written, not the live total: adding the session in
     * progress would count it again when the session ends.
     */
    private static void bind(PreparedStatement statement, PlayerStats stats) throws SQLException {
        statement.setString(1, stats.getName());
        statement.setInt(2, stats.getKills());
        statement.setInt(3, stats.getDeaths());
        statement.setInt(4, stats.getKillstreak());
        statement.setInt(5, stats.getHighestKillstreak());
        statement.setLong(6, stats.storedPlaytimeSeconds());
        statement.setLong(7, stats.getFirstSeen());
        statement.setLong(8, stats.getLastSeen());
    }
}
