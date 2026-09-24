package com.lawkeys.hcfcore.database.dao;

import com.lawkeys.hcfcore.database.migration.SchemaMigrator;
import com.lawkeys.hcfcore.team.JoinMode;
import com.lawkeys.hcfcore.team.SystemZone;
import com.lawkeys.hcfcore.team.Team;
import com.lawkeys.hcfcore.team.TeamRole;
import com.lawkeys.hcfcore.team.TeamSchema;
import com.lawkeys.hcfcore.team.TeamSnapshot;
import com.lawkeys.hcfcore.team.TeamStore;
import com.lawkeys.hcfcore.team.TeamType;
import com.lawkeys.hcfcore.util.WorldPosition;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * JDBC implementation of {@link TeamStore}, valid on both supported backends.
 *
 * <p>Written against a plain {@link DataSource} rather than HikariCP directly, so
 * the same class serves production (MySQL/SQLite behind a Hikari pool) and unit
 * tests (a bare in-memory SQLite connection).
 *
 * <p>Two portability decisions worth noting:
 * <ul>
 *   <li>Writes are update-then-insert instead of an upsert, since MySQL's
 *       {@code ON DUPLICATE KEY UPDATE} and SQLite's {@code ON CONFLICT} differ.</li>
 *   <li>A team's member and alliance rows are replaced wholesale inside the same
 *       transaction: at HCF team sizes that is a handful of rows, and it removes a
 *       whole class of drift between cache and database.</li>
 * </ul>
 *
 * <p><strong>Threading.</strong> Every method blocks; callers must be off the main
 * server thread (CONTRIBUTING.md section 5).
 */
public final class JdbcTeamStore implements TeamStore {

    private final DataSource dataSource;
    private final Consumer<String> logger;

    public JdbcTeamStore(DataSource dataSource, Consumer<String> logger) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void initSchema() throws SQLException {
        new SchemaMigrator(dataSource, logger).migrate(TeamSchema.migrations());
    }

    @Override
    public Collection<Team> loadAll() throws SQLException {
        Map<UUID, Map<UUID, TeamRole>> membersByTeam = loadMembers();
        Map<UUID, Set<UUID>> alliesByTeam = loadAlliances();
        Map<UUID, Map<String, String>> settingsByTeam = loadSettings();

        List<Team> teams = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, name, type, system_zone, leader_uuid, balance, points, koth_captures, "
                             + "rally_world, rally_x, rally_y, rally_z, rally_yaw, rally_pitch, "
                             + "rally_expires_at, created_at FROM hcf_teams");
             ResultSet rs = statement.executeQuery()) {

            while (rs.next()) {
                UUID id = UUID.fromString(rs.getString("id"));
                TeamType type = TeamType.valueOf(rs.getString("type"));
                String rawZone = rs.getString("system_zone");
                SystemZone zone = SystemZone.fromId(rawZone).orElse(null);
                if (type.isSystem() && rawZone != null && zone == null) {
                    // A zone kind removed in a later version: fall back to the one that
                    // takes nothing away from anybody, and say so. TeamSnapshot turns the
                    // null into SAFE for a system team.
                    logger.accept("Unknown system zone '" + rawZone + "' for team " + id + ", defaulting to SAFE");
                }
                String leaderRaw = rs.getString("leader_uuid");
                UUID leader = leaderRaw == null ? null : UUID.fromString(leaderRaw);

                String rallyWorld = rs.getString("rally_world");
                WorldPosition rally = rallyWorld == null ? null : new WorldPosition(
                        rallyWorld,
                        rs.getDouble("rally_x"),
                        rs.getDouble("rally_y"),
                        rs.getDouble("rally_z"),
                        rs.getFloat("rally_yaw"),
                        rs.getFloat("rally_pitch"));

                TeamSnapshot snapshot = new TeamSnapshot(
                        id,
                        rs.getString("name"),
                        type,
                        zone,
                        leader,
                        rs.getLong("created_at"),
                        rs.getDouble("balance"),
                        rs.getLong("points"),
                        rs.getInt("koth_captures"),
                        rally,
                        rs.getLong("rally_expires_at"),
                        membersByTeam.getOrDefault(id, Map.of()),
                        alliesByTeam.getOrDefault(id, Set.of()),
                        permissionsOf(id, settingsByTeam.getOrDefault(id, Map.of())),
                        joinModeOf(settingsByTeam.getOrDefault(id, Map.of())),
                        settingsByTeam.getOrDefault(id, Map.of()).get(DESCRIPTION),
                        settingsByTeam.getOrDefault(id, Map.of()).get(DISCORD));
                teams.add(Team.fromSnapshot(snapshot));
            }
        }
        return teams;
    }

    private Map<UUID, Map<UUID, TeamRole>> loadMembers() throws SQLException {
        Map<UUID, Map<UUID, TeamRole>> byTeam = new HashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT team_id, player_uuid, role FROM hcf_team_members");
             ResultSet rs = statement.executeQuery()) {

            while (rs.next()) {
                UUID teamId = UUID.fromString(rs.getString("team_id"));
                UUID player = UUID.fromString(rs.getString("player_uuid"));
                String rawRole = rs.getString("role");
                TeamRole role = TeamRole.fromId(rawRole).orElse(null);
                if (role == null) {
                    // A role removed or renamed in a later version: keep the player in
                    // the team at the lowest rank rather than silently dropping them.
                    logger.accept("Unknown team role '" + rawRole + "' for " + player + ", defaulting to MEMBER");
                    role = TeamRole.MEMBER;
                }
                byTeam.computeIfAbsent(teamId, ignored -> new LinkedHashMap<>()).put(player, role);
            }
        }
        return byTeam;
    }

    /** {@code hcf_team_settings}: a permission is {@code permission.<key>}; then the team's profile. */
    private static final String PERMISSION_PREFIX = "permission.";
    private static final String JOIN_MODE = "join-mode";
    private static final String DESCRIPTION = "description";
    private static final String DISCORD = "discord";
    /** Written by a build of 24/09/2026 that had only "open or not"; read as {@code join-mode: open}. */
    private static final String LEGACY_OPEN = "open";

    private Map<UUID, Map<String, String>> loadSettings() throws SQLException {
        Map<UUID, Map<String, String>> byTeam = new HashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT team_id, setting_key, setting_value FROM hcf_team_settings");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                byTeam.computeIfAbsent(UUID.fromString(rs.getString("team_id")), ignored -> new HashMap<>())
                        .put(rs.getString("setting_key"), rs.getString("setting_value"));
            }
        }
        return byTeam;
    }

    private static JoinMode joinModeOf(Map<String, String> settings) {
        JoinMode mode = JoinMode.fromId(settings.get(JOIN_MODE)).orElse(null);
        if (mode == null && Boolean.parseBoolean(settings.get(LEGACY_OPEN))) {
            return JoinMode.OPEN;
        }
        return mode;
    }

    private Map<String, TeamRole> permissionsOf(UUID teamId, Map<String, String> settings) {
        Map<String, TeamRole> permissions = new HashMap<>();
        for (Map.Entry<String, String> entry : settings.entrySet()) {
            if (!entry.getKey().startsWith(PERMISSION_PREFIX)) {
                continue;
            }
            String key = entry.getKey().substring(PERMISSION_PREFIX.length());
            TeamRole role = TeamRole.fromId(entry.getValue()).orElse(null);
            if (role == null) {
                // A role removed in a later version: the server's role applies again.
                logger.accept("Unknown role '" + entry.getValue() + "' for permission " + key + " of team "
                        + teamId + "; the server's role applies.");
            } else {
                permissions.put(key, role);
            }
        }
        return permissions;
    }

    private Map<UUID, Set<UUID>> loadAlliances() throws SQLException {
        Map<UUID, Set<UUID>> byTeam = new HashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT team_id, ally_team_id FROM hcf_team_alliances");
             ResultSet rs = statement.executeQuery()) {

            while (rs.next()) {
                UUID teamId = UUID.fromString(rs.getString("team_id"));
                UUID allyId = UUID.fromString(rs.getString("ally_team_id"));
                byTeam.computeIfAbsent(teamId, ignored -> new LinkedHashSet<>()).add(allyId);
            }
        }
        return byTeam;
    }

    @Override
    public void save(Team team) throws SQLException {
        TeamSnapshot snapshot = team.toSnapshot();
        Transactions.run(dataSource, connection -> {
            if (updateTeamRow(connection, snapshot) == 0) {
                insertTeamRow(connection, snapshot);
            }
            replaceMembers(connection, snapshot);
            replaceAlliances(connection, snapshot);
            replaceSettings(connection, snapshot);
        });
    }

    private void replaceSettings(Connection connection, TeamSnapshot snapshot) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM hcf_team_settings WHERE team_id = ?")) {
            delete.setString(1, snapshot.id().toString());
            delete.executeUpdate();
        }
        Map<String, String> rows = new LinkedHashMap<>();
        snapshot.permissions().forEach((key, role) -> rows.put(PERMISSION_PREFIX + key, role.name()));
        if (snapshot.joinMode() != null) {
            rows.put(JOIN_MODE, snapshot.joinMode().configKey());
        }
        if (snapshot.description() != null) {
            rows.put(DESCRIPTION, snapshot.description());
        }
        if (snapshot.discord() != null) {
            rows.put(DISCORD, snapshot.discord());
        }
        if (rows.isEmpty()) {
            return;
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO hcf_team_settings (team_id, setting_key, setting_value) VALUES (?, ?, ?)")) {
            for (Map.Entry<String, String> row : rows.entrySet()) {
                insert.setString(1, snapshot.id().toString());
                insert.setString(2, row.getKey());
                insert.setString(3, row.getValue());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private int updateTeamRow(Connection connection, TeamSnapshot snapshot) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE hcf_teams SET name = ?, type = ?, leader_uuid = ?, balance = ?, points = ?, "
                        + "koth_captures = ?, rally_world = ?, rally_x = ?, rally_y = ?, rally_z = ?, "
                        + "rally_yaw = ?, rally_pitch = ?, rally_expires_at = ?, created_at = ?, "
                        + "system_zone = ? WHERE id = ?")) {
            bindTeamColumns(statement, snapshot, 1);
            statement.setString(16, snapshot.id().toString());
            return statement.executeUpdate();
        }
    }

    private void insertTeamRow(Connection connection, TeamSnapshot snapshot) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO hcf_teams (name, type, leader_uuid, balance, points, koth_captures, "
                        + "rally_world, rally_x, rally_y, rally_z, rally_yaw, rally_pitch, "
                        + "rally_expires_at, created_at, system_zone, id) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            bindTeamColumns(statement, snapshot, 1);
            statement.setString(16, snapshot.id().toString());
            statement.executeUpdate();
        }
    }

    /** Binds the 15 mutable columns starting at {@code offset}; the id is bound by the caller. */
    private void bindTeamColumns(PreparedStatement statement, TeamSnapshot snapshot, int offset) throws SQLException {
        WorldPosition rally = snapshot.rally();
        statement.setString(offset, snapshot.name());
        statement.setString(offset + 1, snapshot.type().name());
        statement.setString(offset + 2, snapshot.leader() == null ? null : snapshot.leader().toString());
        statement.setDouble(offset + 3, snapshot.balance());
        statement.setLong(offset + 4, snapshot.points());
        statement.setInt(offset + 5, snapshot.kothCaptures());
        if (rally == null) {
            statement.setNull(offset + 6, java.sql.Types.VARCHAR);
            statement.setNull(offset + 7, java.sql.Types.DOUBLE);
            statement.setNull(offset + 8, java.sql.Types.DOUBLE);
            statement.setNull(offset + 9, java.sql.Types.DOUBLE);
            statement.setNull(offset + 10, java.sql.Types.REAL);
            statement.setNull(offset + 11, java.sql.Types.REAL);
        } else {
            statement.setString(offset + 6, rally.world());
            statement.setDouble(offset + 7, rally.x());
            statement.setDouble(offset + 8, rally.y());
            statement.setDouble(offset + 9, rally.z());
            statement.setFloat(offset + 10, rally.yaw());
            statement.setFloat(offset + 11, rally.pitch());
        }
        statement.setLong(offset + 12, snapshot.rallyExpiresAt());
        statement.setLong(offset + 13, snapshot.createdAt());
        statement.setString(offset + 14, snapshot.systemZone() == null ? null : snapshot.systemZone().name());
    }

    private void replaceMembers(Connection connection, TeamSnapshot snapshot) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM hcf_team_members WHERE team_id = ?")) {
            delete.setString(1, snapshot.id().toString());
            delete.executeUpdate();
        }
        if (snapshot.members().isEmpty()) {
            return;
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO hcf_team_members (player_uuid, team_id, role) VALUES (?, ?, ?)")) {
            for (Map.Entry<UUID, TeamRole> entry : snapshot.members().entrySet()) {
                insert.setString(1, entry.getKey().toString());
                insert.setString(2, snapshot.id().toString());
                insert.setString(3, entry.getValue().name());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private void replaceAlliances(Connection connection, TeamSnapshot snapshot) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM hcf_team_alliances WHERE team_id = ?")) {
            delete.setString(1, snapshot.id().toString());
            delete.executeUpdate();
        }
        if (snapshot.allies().isEmpty()) {
            return;
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO hcf_team_alliances (team_id, ally_team_id) VALUES (?, ?)")) {
            for (UUID ally : snapshot.allies()) {
                insert.setString(1, snapshot.id().toString());
                insert.setString(2, ally.toString());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    @Override
    public void delete(UUID teamId) throws SQLException {
        Transactions.run(dataSource, connection -> {
            executeDelete(connection, "DELETE FROM hcf_team_members WHERE team_id = ?", teamId);
            executeDelete(connection, "DELETE FROM hcf_team_settings WHERE team_id = ?", teamId);
            executeDelete(connection, "DELETE FROM hcf_team_alliances WHERE team_id = ?", teamId);
            executeDelete(connection, "DELETE FROM hcf_team_alliances WHERE ally_team_id = ?", teamId);
            executeDelete(connection, "DELETE FROM hcf_teams WHERE id = ?", teamId);
        });
    }

    private void executeDelete(Connection connection, String sql, UUID teamId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, teamId.toString());
            statement.executeUpdate();
        }
    }
}
