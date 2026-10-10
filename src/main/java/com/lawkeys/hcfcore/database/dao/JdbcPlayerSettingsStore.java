package com.lawkeys.hcfcore.database.dao;

import com.lawkeys.hcfcore.database.migration.SchemaMigrator;
import com.lawkeys.hcfcore.settings.PlayerSetting;
import com.lawkeys.hcfcore.settings.PlayerSettingsStore;
import com.lawkeys.hcfcore.settings.SettingsSchema;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * JDBC implementation of {@link PlayerSettingsStore}, valid on MySQL and SQLite alike.
 *
 * <p>What a player switched off is stored as a comma-separated list of setting keys.
 * A key this version does not know - a setting a later version removed - is skipped
 * on load rather than failing it.
 */
public final class JdbcPlayerSettingsStore implements PlayerSettingsStore {

    private final DataSource dataSource;
    private final Consumer<String> logger;

    public JdbcPlayerSettingsStore(DataSource dataSource, Consumer<String> logger) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void initSchema() throws SQLException {
        new SchemaMigrator(dataSource, logger).migrate(SettingsSchema.migrations());
    }

    @Override
    public Map<UUID, Set<PlayerSetting>> loadAll() throws SQLException {
        Map<UUID, Set<PlayerSetting>> all = new HashMap<>();
        Set<String> unknown = new TreeSet<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT player_uuid, disabled FROM hcf_player_settings");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                Set<PlayerSetting> off = EnumSet.noneOf(PlayerSetting.class);
                for (String key : rs.getString("disabled").split(",")) {
                    PlayerSetting.byKey(key).ifPresentOrElse(off::add, () -> {
                        if (!key.isBlank()) {
                            unknown.add(key.trim());
                        }
                    });
                }
                if (!off.isEmpty()) {
                    all.put(UUID.fromString(rs.getString("player_uuid")), off);
                }
            }
        }
        // A setting a later version removed: dropped, and said once rather than per player.
        if (!unknown.isEmpty()) {
            logger.accept("Unknown player settings " + unknown + ", ignored.");
        }
        return all;
    }

    @Override
    public void save(UUID playerId, Set<PlayerSetting> disabled) throws SQLException {
        saveAll(Map.of(playerId, disabled));
    }

    @Override
    public void saveAll(Map<UUID, Set<PlayerSetting>> entries) throws SQLException {
        if (entries.isEmpty()) {
            return;
        }
        Transactions.run(dataSource, connection -> {
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM hcf_player_settings WHERE player_uuid = ?");
                 PreparedStatement update = connection.prepareStatement(
                    "UPDATE hcf_player_settings SET disabled = ? WHERE player_uuid = ?");
                 PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO hcf_player_settings (player_uuid, disabled) VALUES (?, ?)")) {

                boolean hasDelete = false;
                Map<UUID, String> toUpsert = new HashMap<>();

                for (Map.Entry<UUID, Set<PlayerSetting>> entry : entries.entrySet()) {
                    UUID id = entry.getKey();
                    Set<PlayerSetting> disabled = entry.getValue();
                    if (disabled.isEmpty()) {
                        delete.setString(1, id.toString());
                        delete.addBatch();
                        hasDelete = true;
                    } else {
                        String keys = disabled.stream().map(PlayerSetting::key).sorted().collect(Collectors.joining(","));
                        toUpsert.put(id, keys);
                    }
                }

                if (hasDelete) {
                    delete.executeBatch();
                }

                if (!toUpsert.isEmpty()) {
                    for (Map.Entry<UUID, String> entry : toUpsert.entrySet()) {
                        update.setString(1, entry.getValue());
                        update.setString(2, entry.getKey().toString());
                        update.addBatch();
                    }
                    int[] results = update.executeBatch();

                    boolean hasInsert = false;
                    int idx = 0;
                    for (Map.Entry<UUID, String> entry : toUpsert.entrySet()) {
                        if (results[idx] == 0) {
                            insert.setString(1, entry.getKey().toString());
                            insert.setString(2, entry.getValue());
                            insert.addBatch();
                            hasInsert = true;
                        }
                        idx++;
                    }
                    if (hasInsert) {
                        insert.executeBatch();
                    }
                }
            }
        });
    }
}
