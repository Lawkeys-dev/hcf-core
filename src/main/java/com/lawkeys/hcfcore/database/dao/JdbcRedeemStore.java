package com.lawkeys.hcfcore.database.dao;

import com.lawkeys.hcfcore.database.migration.SchemaMigrator;
import com.lawkeys.hcfcore.redeem.RedeemCode;
import com.lawkeys.hcfcore.redeem.RedeemSchema;
import com.lawkeys.hcfcore.redeem.RedeemStore;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * JDBC implementation of {@link RedeemStore}, valid on MySQL and SQLite alike.
 *
 * <p>A code's commands are stored one per line: a command typed in chat cannot hold
 * a line break, so the separator can never occur inside one.
 */
public final class JdbcRedeemStore implements RedeemStore {

    private final DataSource dataSource;
    private final Consumer<String> logger;

    public JdbcRedeemStore(DataSource dataSource, Consumer<String> logger) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void initSchema() throws SQLException {
        new SchemaMigrator(dataSource, logger).migrate(RedeemSchema.migrations());
    }

    @Override
    public Collection<RedeemCode> loadAll() throws SQLException {
        Map<String, Set<UUID>> redeemers = new HashMap<>();
        List<RedeemCode> codes = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT code_key, player_uuid FROM hcf_redeem_uses");
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    redeemers.computeIfAbsent(rs.getString("code_key"), key -> new HashSet<>())
                            .add(UUID.fromString(rs.getString("player_uuid")));
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT code_key, code, commands, max_uses, created_by, created_at FROM hcf_redeem_codes");
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String commands = rs.getString("commands");
                    // Trailing empty entries dropped: a command cannot be empty.
                    // JdbcHologramStore keeps them, as a hologram may want blank rows.
                    codes.add(new RedeemCode(
                            rs.getString("code"),
                            commands.isEmpty() ? List.of() : List.of(commands.split("\n")),
                            rs.getInt("max_uses"),
                            rs.getString("created_by"),
                            rs.getLong("created_at"),
                            redeemers.getOrDefault(rs.getString("code_key"), Set.of())));
                }
            }
        }
        return codes;
    }

    @Override
    public void saveCode(RedeemCode code) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE hcf_redeem_codes SET code = ?, commands = ?, max_uses = ?, created_by = ?, "
                            + "created_at = ? WHERE code_key = ?")) {
                bind(update, code);
                if (update.executeUpdate() > 0) {
                    return;
                }
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO hcf_redeem_codes (code, commands, max_uses, created_by, created_at, code_key) "
                            + "VALUES (?, ?, ?, ?, ?, ?)")) {
                bind(insert, code);
                insert.executeUpdate();
            }
        }
    }

    private static void bind(PreparedStatement statement, RedeemCode code) throws SQLException {
        statement.setString(1, code.code());
        statement.setString(2, String.join("\n", code.commands()));
        statement.setInt(3, code.maxUses());
        statement.setString(4, code.createdBy());
        statement.setLong(5, code.createdAt());
        statement.setString(6, code.key());
    }

    @Override
    public void deleteCode(String key) throws SQLException {
        Transactions.run(dataSource, connection -> {
            execute(connection, "DELETE FROM hcf_redeem_uses WHERE code_key = ?", key);
            execute(connection, "DELETE FROM hcf_redeem_codes WHERE code_key = ?", key);
        });
    }

    /**
     * Delete, then insert, in one transaction - so writing the same redemption twice,
     * as a retry after a failure that happened past the insert would, cannot trip the
     * primary key and stall every write queued behind it.
     */
    @Override
    public void addRedemption(String key, UUID playerId, long at) throws SQLException {
        Transactions.run(dataSource, connection -> {
            execute(connection, "DELETE FROM hcf_redeem_uses WHERE code_key = ? AND player_uuid = ?",
                    key, playerId.toString());
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO hcf_redeem_uses (code_key, player_uuid, redeemed_at) VALUES (?, ?, ?)")) {
                insert.setString(1, key);
                insert.setString(2, playerId.toString());
                insert.setLong(3, at);
                insert.executeUpdate();
            }
        });
    }

    @Override
    public void clearRedemptions(String key, UUID playerId) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            if (playerId == null) {
                execute(connection, "DELETE FROM hcf_redeem_uses WHERE code_key = ?", key);
            } else {
                execute(connection, "DELETE FROM hcf_redeem_uses WHERE code_key = ? AND player_uuid = ?",
                        key, playerId.toString());
            }
        }
    }

    private static void execute(Connection connection, String sql, String... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                statement.setString(i + 1, values[i]);
            }
            statement.executeUpdate();
        }
    }
}
