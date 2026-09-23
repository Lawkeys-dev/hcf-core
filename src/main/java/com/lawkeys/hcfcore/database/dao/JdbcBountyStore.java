package com.lawkeys.hcfcore.database.dao;

import com.lawkeys.hcfcore.database.migration.SchemaMigrator;
import com.lawkeys.hcfcore.economy.bounty.BountySchema;
import com.lawkeys.hcfcore.economy.bounty.BountyStore;

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

/** JDBC implementation of {@link BountyStore}, valid on MySQL and SQLite alike. */
public final class JdbcBountyStore implements BountyStore {

    private final DataSource dataSource;
    private final Consumer<String> logger;

    public JdbcBountyStore(DataSource dataSource, Consumer<String> logger) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void initSchema() throws SQLException {
        new SchemaMigrator(dataSource, logger).migrate(BountySchema.migrations());
    }

    @Override
    public Map<UUID, Double> loadAll() throws SQLException {
        Map<UUID, Double> all = new HashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT target_uuid, amount FROM hcf_bounties");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                all.put(UUID.fromString(rs.getString("target_uuid")), rs.getDouble("amount"));
            }
        }
        return all;
    }

    @Override
    public void save(UUID target, double amount) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            if (amount <= 0) {
                try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM hcf_bounties WHERE target_uuid = ?")) {
                    delete.setString(1, target.toString());
                    delete.executeUpdate();
                }
                return;
            }
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE hcf_bounties SET amount = ? WHERE target_uuid = ?")) {
                update.setDouble(1, amount);
                update.setString(2, target.toString());
                if (update.executeUpdate() > 0) {
                    return;
                }
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO hcf_bounties (target_uuid, amount) VALUES (?, ?)")) {
                insert.setString(1, target.toString());
                insert.setDouble(2, amount);
                insert.executeUpdate();
            }
        }
    }
}
