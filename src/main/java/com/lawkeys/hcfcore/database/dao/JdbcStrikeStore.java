package com.lawkeys.hcfcore.database.dao;

import com.lawkeys.hcfcore.database.migration.SchemaMigrator;
import com.lawkeys.hcfcore.staff.StaffSchema;
import com.lawkeys.hcfcore.staff.strike.Strike;
import com.lawkeys.hcfcore.staff.strike.StrikeStore;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/** JDBC implementation of {@link StrikeStore}, valid on MySQL and SQLite alike. */
public final class JdbcStrikeStore implements StrikeStore {

    private final DataSource dataSource;
    private final Consumer<String> logger;

    public JdbcStrikeStore(DataSource dataSource, Consumer<String> logger) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void initSchema() throws SQLException {
        new SchemaMigrator(dataSource, logger).migrate(StaffSchema.migrations());
    }

    @Override
    public Collection<Strike> loadAll() throws SQLException {
        Collection<Strike> strikes = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, team_uuid, team_name, subject, reason, issued_by, issued_at, expires_at "
                             + "FROM hcf_team_strikes");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                strikes.add(new Strike(
                        rs.getLong("id"),
                        UUID.fromString(rs.getString("team_uuid")),
                        rs.getString("team_name"),
                        rs.getString("subject"),
                        rs.getString("reason"),
                        rs.getString("issued_by"),
                        rs.getLong("issued_at"),
                        rs.getLong("expires_at")));
            }
        }
        return strikes;
    }

    @Override
    public void save(Strike strike) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE hcf_team_strikes SET team_uuid = ?, team_name = ?, subject = ?, reason = ?, "
                            + "issued_by = ?, issued_at = ?, expires_at = ? WHERE id = ?")) {
                bind(update, strike);
                update.setLong(8, strike.id());
                if (update.executeUpdate() > 0) {
                    return;
                }
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO hcf_team_strikes (team_uuid, team_name, subject, reason, issued_by, "
                            + "issued_at, expires_at, id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                bind(insert, strike);
                insert.setLong(8, strike.id());
                insert.executeUpdate();
            }
        }
    }

    private static void bind(PreparedStatement statement, Strike strike) throws SQLException {
        statement.setString(1, strike.teamId().toString());
        statement.setString(2, strike.teamName());
        statement.setString(3, strike.subject());
        statement.setString(4, strike.reason());
        statement.setString(5, strike.issuedBy());
        statement.setLong(6, strike.issuedAt());
        statement.setLong(7, strike.expiresAt());
    }

    @Override
    public void delete(long id) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM hcf_team_strikes WHERE id = ?")) {
            statement.setLong(1, id);
            statement.executeUpdate();
        }
    }
}
