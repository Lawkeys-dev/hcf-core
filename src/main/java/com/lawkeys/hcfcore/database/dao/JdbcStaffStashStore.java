package com.lawkeys.hcfcore.database.dao;

import com.lawkeys.hcfcore.database.migration.SchemaMigrator;
import com.lawkeys.hcfcore.staff.StaffSchema;
import com.lawkeys.hcfcore.staff.StaffStashStore;

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
 * JDBC implementation of {@link StaffStashStore}, valid on MySQL and SQLite alike.
 *
 * <p><strong>Threading.</strong> Blocking; async task only (CONTRIBUTING.md section 5).
 */
public final class JdbcStaffStashStore implements StaffStashStore {

    private final DataSource dataSource;
    private final Consumer<String> logger;

    public JdbcStaffStashStore(DataSource dataSource, Consumer<String> logger) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void initSchema() throws SQLException {
        new SchemaMigrator(dataSource, logger).migrate(StaffSchema.migrations());
    }

    @Override
    public Map<UUID, byte[]> loadAll() throws SQLException {
        Map<UUID, byte[]> stashes = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT player_uuid, contents FROM hcf_staff_stashes");
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
                    "UPDATE hcf_staff_stashes SET contents = ? WHERE player_uuid = ?")) {
                update.setBytes(1, contents);
                update.setString(2, playerId.toString());
                if (update.executeUpdate() > 0) {
                    return;
                }
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO hcf_staff_stashes (contents, player_uuid) VALUES (?, ?)")) {
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
                     "DELETE FROM hcf_staff_stashes WHERE player_uuid = ?")) {
            statement.setString(1, playerId.toString());
            statement.executeUpdate();
        }
    }

    @Override
    public void deleteAll(Iterable<UUID> playerIds) throws SQLException {
        if (!playerIds.iterator().hasNext()) {
            return;
        }
        Transactions.run(dataSource, connection -> {
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM hcf_staff_stashes WHERE player_uuid = ?")) {
                for (UUID playerId : playerIds) {
                    delete.setString(1, playerId.toString());
                    delete.addBatch();
                }
                delete.executeBatch();
            }
        });
    }

    @Override
    public void saveAll(Map<UUID, byte[]> stashes) throws SQLException {
        if (stashes.isEmpty()) {
            return;
        }
        Transactions.run(dataSource, connection -> {
            Map<UUID, byte[]> toInsert = new LinkedHashMap<>();
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE hcf_staff_stashes SET contents = ? WHERE player_uuid = ?")) {
                for (Map.Entry<UUID, byte[]> entry : stashes.entrySet()) {
                    update.setBytes(1, entry.getValue());
                    update.setString(2, entry.getKey().toString());
                    update.addBatch();
                }
                int[] counts = update.executeBatch();
                int idx = 0;
                for (Map.Entry<UUID, byte[]> entry : stashes.entrySet()) {
                    // PreparedStatement.SUCCESS_NO_INFO is -2, >= 0 is row count. 0 means updated 0 rows.
                    if (idx < counts.length && counts[idx] == 0) {
                        toInsert.put(entry.getKey(), entry.getValue());
                    }
                    idx++;
                }
            }
            if (!toInsert.isEmpty()) {
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO hcf_staff_stashes (contents, player_uuid) VALUES (?, ?)")) {
                    for (Map.Entry<UUID, byte[]> entry : toInsert.entrySet()) {
                        insert.setBytes(1, entry.getValue());
                        insert.setString(2, entry.getKey().toString());
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
            }
        });
    }
}
