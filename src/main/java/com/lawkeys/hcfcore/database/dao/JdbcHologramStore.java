package com.lawkeys.hcfcore.database.dao;

import com.lawkeys.hcfcore.database.migration.SchemaMigrator;
import com.lawkeys.hcfcore.hologram.Hologram;
import com.lawkeys.hcfcore.hologram.HologramSchema;
import com.lawkeys.hcfcore.hologram.HologramStore;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** JDBC implementation of {@link HologramStore}, valid on MySQL and SQLite alike. */
public final class JdbcHologramStore implements HologramStore {

    private final DataSource dataSource;
    private final Consumer<String> logger;

    public JdbcHologramStore(DataSource dataSource, Consumer<String> logger) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void initSchema() throws SQLException {
        new SchemaMigrator(dataSource, logger).migrate(HologramSchema.migrations());
    }

    @Override
    public Collection<Hologram> loadAll() throws SQLException {
        List<Hologram> all = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, world, x, y, z, `lines` FROM hcf_holograms");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                String lines = rs.getString("lines");
                // -1 keeps trailing empty lines: a hologram may want blank rows at the
                // bottom. JdbcRedeemStore drops them, as a command cannot be empty.
                all.add(new Hologram(rs.getString("id"), rs.getString("world"),
                        rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                        lines.isEmpty() ? List.of() : List.of(lines.split("\n", -1))));
            }
        }
        return all;
    }

    @Override
    public void save(Hologram hologram) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE hcf_holograms SET world = ?, x = ?, y = ?, z = ?, `lines` = ? WHERE id = ?")) {
                bind(update, hologram);
                if (update.executeUpdate() > 0) {
                    return;
                }
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO hcf_holograms (world, x, y, z, `lines`, id) VALUES (?, ?, ?, ?, ?, ?)")) {
                bind(insert, hologram);
                insert.executeUpdate();
            }
        }
    }

    private static void bind(PreparedStatement statement, Hologram hologram) throws SQLException {
        statement.setString(1, hologram.world());
        statement.setDouble(2, hologram.x());
        statement.setDouble(3, hologram.y());
        statement.setDouble(4, hologram.z());
        statement.setString(5, String.join("\n", hologram.lines()));
        statement.setString(6, hologram.id());
    }

    @Override
    public void delete(String id) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM hcf_holograms WHERE id = ?")) {
            statement.setString(1, id);
            statement.executeUpdate();
        }
    }
}
