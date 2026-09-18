package com.lawkeys.hcfcore.database.dao;

import com.lawkeys.hcfcore.database.migration.SchemaMigrator;
import com.lawkeys.hcfcore.economy.EconomySchema;
import com.lawkeys.hcfcore.economy.EconomyStore;

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
 * JDBC implementation of {@link EconomyStore}, valid on MySQL and SQLite alike.
 *
 * <p><strong>Threading.</strong> Blocking; async task only (CONTRIBUTING.md section 5).
 */
public final class JdbcEconomyStore implements EconomyStore {

    private final DataSource dataSource;
    private final Consumer<String> logger;

    public JdbcEconomyStore(DataSource dataSource, Consumer<String> logger) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void initSchema() throws SQLException {
        new SchemaMigrator(dataSource, logger).migrate(EconomySchema.migrations());
    }

    @Override
    public Map<UUID, Double> loadAll() throws SQLException {
        Map<UUID, Double> balances = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT player_uuid, balance FROM hcf_balances");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                balances.put(UUID.fromString(rs.getString("player_uuid")), rs.getDouble("balance"));
            }
        }
        return balances;
    }

    @Override
    public void save(UUID playerId, double balance) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE hcf_balances SET balance = ? WHERE player_uuid = ?")) {
                update.setDouble(1, balance);
                update.setString(2, playerId.toString());
                if (update.executeUpdate() > 0) {
                    return;
                }
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO hcf_balances (balance, player_uuid) VALUES (?, ?)")) {
                insert.setDouble(1, balance);
                insert.setString(2, playerId.toString());
                insert.executeUpdate();
            }
        }
    }
}
