package com.lawkeys.hcfcore.database.dao;

import com.lawkeys.hcfcore.database.migration.SchemaMigrator;
import com.lawkeys.hcfcore.dtr.DtrSchema;
import com.lawkeys.hcfcore.dtr.DtrState;
import com.lawkeys.hcfcore.dtr.DtrStore;

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
 * JDBC implementation of {@link DtrStore}, valid on MySQL and SQLite alike.
 *
 * <p>Update-then-insert rather than an upsert, for the same portability reason as
 * the other stores: the two backends spell upserts differently.
 *
 * <p><strong>Threading.</strong> Blocking; async task only (CONTRIBUTING.md section 5).
 */
public final class JdbcDtrStore implements DtrStore {

    private final DataSource dataSource;
    private final Consumer<String> logger;

    public JdbcDtrStore(DataSource dataSource, Consumer<String> logger) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void initSchema() throws SQLException {
        new SchemaMigrator(dataSource, logger).migrate(DtrSchema.migrations());
    }

    @Override
    public Collection<DtrState> loadAll() throws SQLException {
        List<DtrState> states = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT team_id, stored_dtr, regen_at FROM hcf_team_dtr");
             ResultSet rs = statement.executeQuery()) {

            while (rs.next()) {
                states.add(new DtrState(UUID.fromString(rs.getString("team_id")),
                        rs.getDouble("stored_dtr"), rs.getLong("regen_at")));
            }
        }
        return states;
    }

    @Override
    public void save(DtrState state) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE hcf_team_dtr SET stored_dtr = ?, regen_at = ? WHERE team_id = ?")) {
                update.setDouble(1, state.stored());
                update.setLong(2, state.regenAt());
                update.setString(3, state.teamId().toString());
                if (update.executeUpdate() > 0) {
                    return;
                }
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO hcf_team_dtr (stored_dtr, regen_at, team_id) VALUES (?, ?, ?)")) {
                insert.setDouble(1, state.stored());
                insert.setLong(2, state.regenAt());
                insert.setString(3, state.teamId().toString());
                insert.executeUpdate();
            }
        }
    }

    @Override
    public void delete(UUID teamId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM hcf_team_dtr WHERE team_id = ?")) {
            statement.setString(1, teamId.toString());
            statement.executeUpdate();
        }
    }
}
