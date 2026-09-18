package com.lawkeys.hcfcore.database.dao;

import com.lawkeys.hcfcore.database.migration.SchemaMigrator;
import com.lawkeys.hcfcore.phase.PhaseSchema;
import com.lawkeys.hcfcore.phase.PhaseState;
import com.lawkeys.hcfcore.phase.PhaseStore;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * JDBC implementation of {@link PhaseStore}, valid on MySQL and SQLite alike.
 *
 * <p>The phase is a single row (id 1). A save replaces it and the list of players
 * who enabled PvP in one transaction, so the two can never disagree.
 *
 * <p><strong>Threading.</strong> Blocking; async task only (CONTRIBUTING.md section 5).
 */
public final class JdbcPhaseStore implements PhaseStore {

    private static final int ROW = 1;

    private final DataSource dataSource;
    private final Consumer<String> logger;

    public JdbcPhaseStore(DataSource dataSource, Consumer<String> logger) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void initSchema() throws SQLException {
        new SchemaMigrator(dataSource, logger).migrate(PhaseSchema.migrations());
    }

    @Override
    public Snapshot load() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            PhaseState state = PhaseState.NONE;
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT sotw_ends_at, eotw_since, sotw_schedule_handled, eotw_schedule_handled, "
                            + "purge_ends_at FROM hcf_map_phase WHERE id = ?")) {
                statement.setInt(1, ROW);
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        state = new PhaseState(rs.getLong("sotw_ends_at"), rs.getLong("eotw_since"),
                                rs.getLong("sotw_schedule_handled"), rs.getLong("eotw_schedule_handled"),
                                rs.getLong("purge_ends_at"));
                    }
                }
            }
            Set<UUID> enabled = new HashSet<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT player_uuid FROM hcf_sotw_pvp_enabled");
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    enabled.add(UUID.fromString(rs.getString("player_uuid")));
                }
            }
            return new Snapshot(state, enabled);
        }
    }

    @Override
    public void save(PhaseState state, Set<UUID> sotwPvpEnabled) throws SQLException {
        Transactions.run(dataSource, connection -> {
            if (updateRow(connection, state) == 0) {
                insertRow(connection, state);
            }
            try (PreparedStatement clear = connection.prepareStatement("DELETE FROM hcf_sotw_pvp_enabled")) {
                clear.executeUpdate();
            }
            if (!sotwPvpEnabled.isEmpty()) {
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO hcf_sotw_pvp_enabled (player_uuid) VALUES (?)")) {
                    for (UUID player : sotwPvpEnabled) {
                        insert.setString(1, player.toString());
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
            }
        });
    }

    private static int updateRow(Connection connection, PhaseState state) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE hcf_map_phase SET sotw_ends_at = ?, eotw_since = ?, sotw_schedule_handled = ?, "
                        + "eotw_schedule_handled = ?, purge_ends_at = ? WHERE id = ?")) {
            update.setLong(1, state.sotwEndsAt());
            update.setLong(2, state.eotwSince());
            update.setLong(3, state.sotwScheduleHandled());
            update.setLong(4, state.eotwScheduleHandled());
            update.setLong(5, state.purgeEndsAt());
            update.setInt(6, ROW);
            return update.executeUpdate();
        }
    }

    private static void insertRow(Connection connection, PhaseState state) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO hcf_map_phase (sotw_ends_at, eotw_since, sotw_schedule_handled, "
                        + "eotw_schedule_handled, purge_ends_at, id) VALUES (?, ?, ?, ?, ?, ?)")) {
            insert.setLong(1, state.sotwEndsAt());
            insert.setLong(2, state.eotwSince());
            insert.setLong(3, state.sotwScheduleHandled());
            insert.setLong(4, state.eotwScheduleHandled());
            insert.setLong(5, state.purgeEndsAt());
            insert.setInt(6, ROW);
            insert.executeUpdate();
        }
    }
}
