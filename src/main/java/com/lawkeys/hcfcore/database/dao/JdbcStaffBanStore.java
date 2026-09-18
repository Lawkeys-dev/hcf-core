package com.lawkeys.hcfcore.database.dao;

import com.lawkeys.hcfcore.database.migration.SchemaMigrator;
import com.lawkeys.hcfcore.staff.StaffBan;
import com.lawkeys.hcfcore.staff.StaffBanStore;
import com.lawkeys.hcfcore.staff.StaffSchema;

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
 * JDBC implementation of {@link StaffBanStore}, valid on MySQL and SQLite alike.
 *
 * <p><strong>Threading.</strong> Blocking; async task only (CONTRIBUTING.md section 5).
 */
public final class JdbcStaffBanStore implements StaffBanStore {

    private final DataSource dataSource;
    private final Consumer<String> logger;

    public JdbcStaffBanStore(DataSource dataSource, Consumer<String> logger) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void initSchema() throws SQLException {
        new SchemaMigrator(dataSource, logger).migrate(StaffSchema.migrations());
    }

    @Override
    public Map<UUID, StaffBan> loadAll() throws SQLException {
        Map<UUID, StaffBan> bans = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT player_uuid, reason, banned_by, banned_at FROM hcf_staff_bans");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                UUID playerId = UUID.fromString(rs.getString("player_uuid"));
                bans.put(playerId, new StaffBan(playerId, rs.getString("reason"),
                        rs.getString("banned_by"), rs.getLong("banned_at")));
            }
        }
        return bans;
    }

    @Override
    public void save(StaffBan ban) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE hcf_staff_bans SET reason = ?, banned_by = ?, banned_at = ? "
                            + "WHERE player_uuid = ?")) {
                update.setString(1, ban.reason());
                update.setString(2, ban.bannedBy());
                update.setLong(3, ban.bannedAt());
                update.setString(4, ban.playerId().toString());
                if (update.executeUpdate() > 0) {
                    return;
                }
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO hcf_staff_bans (reason, banned_by, banned_at, player_uuid) "
                            + "VALUES (?, ?, ?, ?)")) {
                insert.setString(1, ban.reason());
                insert.setString(2, ban.bannedBy());
                insert.setLong(3, ban.bannedAt());
                insert.setString(4, ban.playerId().toString());
                insert.executeUpdate();
            }
        }
    }

    @Override
    public void delete(UUID playerId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM hcf_staff_bans WHERE player_uuid = ?")) {
            statement.setString(1, playerId.toString());
            statement.executeUpdate();
        }
    }
}
