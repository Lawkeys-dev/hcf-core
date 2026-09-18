package com.lawkeys.hcfcore.database.dao;

import com.lawkeys.hcfcore.database.migration.SchemaMigrator;
import com.lawkeys.hcfcore.kit.Kit;
import com.lawkeys.hcfcore.kit.KitSchema;
import com.lawkeys.hcfcore.kit.KitStore;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * JDBC implementation of {@link KitStore}, valid on MySQL and SQLite alike.
 *
 * <p><strong>Threading.</strong> Blocking; async task only (CONTRIBUTING.md section 5).
 */
public final class JdbcKitStore implements KitStore {

    private final DataSource dataSource;
    private final Consumer<String> logger;

    public JdbcKitStore(DataSource dataSource, Consumer<String> logger) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void initSchema() throws SQLException {
        new SchemaMigrator(dataSource, logger).migrate(KitSchema.migrations());
    }

    @Override
    public Collection<Kit> loadKits() throws SQLException {
        Collection<Kit> kits = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, display_name, permission, cooldown_seconds, contents FROM hcf_kits");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                kits.add(new Kit(rs.getString("id"), rs.getString("display_name"),
                        rs.getString("permission"),
                        rs.getLong("cooldown_seconds"), rs.getBytes("contents")));
            }
        }
        return kits;
    }

    @Override
    public Map<UUID, Map<String, Long>> loadCooldowns() throws SQLException {
        Map<UUID, Map<String, Long>> all = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT player_uuid, kit_id, expires_at FROM hcf_kit_cooldowns");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                all.computeIfAbsent(UUID.fromString(rs.getString("player_uuid")),
                                id -> new LinkedHashMap<>())
                        .put(rs.getString("kit_id"), rs.getLong("expires_at"));
            }
        }
        return all;
    }

    @Override
    public void saveKit(Kit kit) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE hcf_kits SET display_name = ?, permission = ?, "
                            + "cooldown_seconds = ?, contents = ? WHERE id = ?")) {
                bind(update, kit);
                update.setString(5, kit.id());
                if (update.executeUpdate() > 0) {
                    return;
                }
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO hcf_kits (display_name, permission, cooldown_seconds, "
                            + "contents, id) VALUES (?, ?, ?, ?, ?)")) {
                bind(insert, kit);
                insert.setString(5, kit.id());
                insert.executeUpdate();
            }
        }
    }

    private static void bind(PreparedStatement statement, Kit kit) throws SQLException {
        statement.setString(1, kit.displayName());
        statement.setString(2, kit.permission());
        statement.setLong(3, kit.cooldownSeconds());
        statement.setBytes(4, kit.contents());
    }

    /**
     * The waits and the layouts go with the kit, in the same transaction: a kit of the
     * same name created later must not inherit somebody's old cooldown or layout.
     */
    @Override
    public void deleteKit(String id) throws SQLException {
        Transactions.run(dataSource, connection -> {
            for (String sql : new String[] {
                    "DELETE FROM hcf_kits WHERE id = ?",
                    "DELETE FROM hcf_kit_cooldowns WHERE kit_id = ?",
                    "DELETE FROM hcf_kit_layouts WHERE kit_id = ?"}) {
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, id);
                    statement.executeUpdate();
                }
            }
        });
    }

    @Override
    public Map<UUID, Map<String, String>> loadLayouts() throws SQLException {
        Map<UUID, Map<String, String>> all = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT player_uuid, kit_id, layout FROM hcf_kit_layouts");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                all.computeIfAbsent(UUID.fromString(rs.getString("player_uuid")),
                                id -> new LinkedHashMap<>())
                        .put(rs.getString("kit_id"), rs.getString("layout"));
            }
        }
        return all;
    }

    /** Replaces this player's layouts with exactly the set given, like {@link #saveCooldowns}. */
    @Override
    public void saveLayouts(UUID playerId, Map<String, String> layouts) throws SQLException {
        replaceRows(playerId, "DELETE FROM hcf_kit_layouts WHERE player_uuid = ?",
                "INSERT INTO hcf_kit_layouts (player_uuid, kit_id, layout) VALUES (?, ?, ?)",
                layouts, (insert, value) -> insert.setString(3, value));
    }

    /**
     * Replaces this player's waits with exactly the set given.
     *
     * <p>Delete-then-insert in one transaction rather than a diff: the set is a
     * handful of rows, and working out which ones changed would cost more than
     * rewriting them.
     */
    @Override
    public void saveCooldowns(UUID playerId, Map<String, Long> cooldowns) throws SQLException {
        replaceRows(playerId, "DELETE FROM hcf_kit_cooldowns WHERE player_uuid = ?",
                "INSERT INTO hcf_kit_cooldowns (player_uuid, kit_id, expires_at) VALUES (?, ?, ?)",
                cooldowns, (insert, value) -> insert.setLong(3, value));
    }

    /** Binds the third column of one row. */
    @FunctionalInterface
    private interface ValueBinder<V> {
        void bind(PreparedStatement insert, V value) throws SQLException;
    }

    /** Delete this player's rows, insert these, in one transaction. */
    private <V> void replaceRows(UUID playerId, String deleteSql, String insertSql, Map<String, V> rows,
                                 ValueBinder<V> binder) throws SQLException {
        Transactions.run(dataSource, connection -> {
            try (PreparedStatement delete = connection.prepareStatement(deleteSql)) {
                delete.setString(1, playerId.toString());
                delete.executeUpdate();
            }
            if (!rows.isEmpty()) {
                try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
                    for (Map.Entry<String, V> entry : rows.entrySet()) {
                        insert.setString(1, playerId.toString());
                        insert.setString(2, entry.getKey());
                        binder.bind(insert, entry.getValue());
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
            }
        });
    }
}
