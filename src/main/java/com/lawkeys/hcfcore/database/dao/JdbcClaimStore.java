package com.lawkeys.hcfcore.database.dao;

import com.lawkeys.hcfcore.claim.Claim;
import com.lawkeys.hcfcore.claim.ClaimArea;
import com.lawkeys.hcfcore.claim.ClaimSchema;
import com.lawkeys.hcfcore.claim.ClaimStore;
import com.lawkeys.hcfcore.claim.HomeType;
import com.lawkeys.hcfcore.claim.TeamHome;
import com.lawkeys.hcfcore.database.migration.SchemaMigrator;
import com.lawkeys.hcfcore.util.ChunkPosition;
import com.lawkeys.hcfcore.util.WorldPosition;

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
 * JDBC implementation of {@link ClaimStore}, valid on MySQL and SQLite alike.
 *
 * <p>A team's claims are written as delete-then-insert inside one transaction.
 * That is the same trade-off as {@code JdbcTeamStore} makes for members: a team
 * holds a handful of claims, and replacing the set wholesale removes any chance of
 * the table drifting from the in-memory cache.
 *
 * <p><strong>Threading.</strong> Blocking; async task only (CONTRIBUTING.md section 5).
 */
public final class JdbcClaimStore implements ClaimStore {

    private final DataSource dataSource;
    private final Consumer<String> logger;

    public JdbcClaimStore(DataSource dataSource, Consumer<String> logger) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void initSchema() throws SQLException {
        new SchemaMigrator(dataSource, logger).migrate(ClaimSchema.migrations());
    }

    @Override
    public Collection<ClaimArea> loadClaims() throws SQLException {
        List<ClaimArea> claims = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, team_id, world, min_x, min_z, max_x, max_z, price_paid, claimed_at "
                             + "FROM hcf_claim_areas");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                claims.add(new ClaimArea(UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("team_id")), rs.getString("world"),
                        rs.getInt("min_x"), rs.getInt("min_z"), rs.getInt("max_x"), rs.getInt("max_z"),
                        rs.getDouble("price_paid"), rs.getLong("claimed_at")));
            }
        }
        return claims;
    }

    @Override
    public void clearLegacyChunkClaims() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM hcf_team_claims")) {
            statement.executeUpdate();
        }
    }

    @Override
    public Collection<Claim> loadLegacyChunkClaims() throws SQLException {
        List<Claim> claims = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT world, chunk_x, chunk_z, team_id, claimed_at FROM hcf_team_claims");
             ResultSet rs = statement.executeQuery()) {

            while (rs.next()) {
                ChunkPosition chunk = new ChunkPosition(
                        rs.getString("world"), rs.getInt("chunk_x"), rs.getInt("chunk_z"));
                claims.add(new Claim(UUID.fromString(rs.getString("team_id")), chunk,
                        rs.getLong("claimed_at")));
            }
        }
        return claims;
    }

    @Override
    public Collection<TeamHome> loadHomes() throws SQLException {
        List<TeamHome> homes = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT team_id, home_type, world, x, y, z, yaw, pitch, set_at FROM hcf_team_homes");
             ResultSet rs = statement.executeQuery()) {

            while (rs.next()) {
                String rawType = rs.getString("home_type");
                HomeType type = HomeType.fromId(rawType).orElse(null);
                if (type == null) {
                    // A home type removed in a later version: skip it rather than
                    // fail the whole load, and say so.
                    logger.accept("Unknown team home type '" + rawType + "', ignored.");
                    continue;
                }
                WorldPosition position = new WorldPosition(
                        rs.getString("world"), rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                        rs.getFloat("yaw"), rs.getFloat("pitch"));
                homes.add(new TeamHome(UUID.fromString(rs.getString("team_id")), type, position,
                        rs.getLong("set_at")));
            }
        }
        return homes;
    }

    @Override
    public void saveClaims(UUID teamId, Collection<ClaimArea> claims) throws SQLException {
        Transactions.run(dataSource, connection -> {
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM hcf_claim_areas WHERE team_id = ?")) {
                delete.setString(1, teamId.toString());
                delete.executeUpdate();
            }
            if (!claims.isEmpty()) {
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO hcf_claim_areas (id, team_id, world, min_x, min_z, max_x, max_z, "
                                + "price_paid, claimed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    for (ClaimArea claim : claims) {
                        insert.setString(1, claim.id().toString());
                        insert.setString(2, claim.teamId().toString());
                        insert.setString(3, claim.world());
                        insert.setInt(4, claim.minX());
                        insert.setInt(5, claim.minZ());
                        insert.setInt(6, claim.maxX());
                        insert.setInt(7, claim.maxZ());
                        insert.setDouble(8, claim.pricePaid());
                        insert.setLong(9, claim.claimedAt());
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
            }
        });
    }

    @Override
    public void saveHome(TeamHome home) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            if (updateHome(connection, home) == 0) {
                insertHome(connection, home);
            }
        }
    }

    private int updateHome(Connection connection, TeamHome home) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE hcf_team_homes SET world = ?, x = ?, y = ?, z = ?, yaw = ?, pitch = ?, "
                        + "set_at = ? WHERE team_id = ? AND home_type = ?")) {
            bindPosition(statement, home, 1);
            statement.setString(8, home.teamId().toString());
            statement.setString(9, home.type().name());
            return statement.executeUpdate();
        }
    }

    private void insertHome(Connection connection, TeamHome home) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO hcf_team_homes (world, x, y, z, yaw, pitch, set_at, team_id, home_type) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            bindPosition(statement, home, 1);
            statement.setString(8, home.teamId().toString());
            statement.setString(9, home.type().name());
            statement.executeUpdate();
        }
    }

    /** Binds world, x, y, z, yaw, pitch and set_at starting at {@code offset}. */
    private void bindPosition(PreparedStatement statement, TeamHome home, int offset) throws SQLException {
        WorldPosition position = home.position();
        statement.setString(offset, position.world());
        statement.setDouble(offset + 1, position.x());
        statement.setDouble(offset + 2, position.y());
        statement.setDouble(offset + 3, position.z());
        statement.setFloat(offset + 4, position.yaw());
        statement.setFloat(offset + 5, position.pitch());
        statement.setLong(offset + 6, home.setAt());
    }

    @Override
    public void deleteHome(UUID teamId, HomeType type) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM hcf_team_homes WHERE team_id = ? AND home_type = ?")) {
            statement.setString(1, teamId.toString());
            statement.setString(2, type.name());
            statement.executeUpdate();
        }
    }

    @Override
    public void deleteTeam(UUID teamId) throws SQLException {
        Transactions.run(dataSource, connection -> {
            executeDelete(connection, "DELETE FROM hcf_claim_areas WHERE team_id = ?", teamId);
            executeDelete(connection, "DELETE FROM hcf_team_claims WHERE team_id = ?", teamId);
            executeDelete(connection, "DELETE FROM hcf_team_homes WHERE team_id = ?", teamId);
        });
    }

    private void executeDelete(Connection connection, String sql, UUID teamId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, teamId.toString());
            statement.executeUpdate();
        }
    }
}
