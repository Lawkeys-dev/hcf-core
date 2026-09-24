package com.lawkeys.hcfcore.database;

import com.lawkeys.hcfcore.database.dao.JdbcTeamStore;
import com.lawkeys.hcfcore.database.migration.Migration;
import com.lawkeys.hcfcore.database.migration.SchemaMigrator;
import com.lawkeys.hcfcore.team.SystemZone;
import com.lawkeys.hcfcore.team.JoinMode;
import com.lawkeys.hcfcore.team.Team;
import com.lawkeys.hcfcore.team.TeamRole;
import com.lawkeys.hcfcore.team.TeamSchema;
import com.lawkeys.hcfcore.team.TeamSnapshot;
import com.lawkeys.hcfcore.team.TeamType;
import com.lawkeys.hcfcore.util.WorldPosition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the real SQL against a real SQLite database.
 *
 * <p>SQLite is the project's supported dev/solo backend (ARCHITECTURE.md
 * section 4), so this is not a mock: the same statements run in production
 * against MySQL, and every statement here is written to be valid on both.
 *
 * <p>A temporary file rather than {@code :memory:} because the store opens a
 * fresh connection per operation, and each connection to an in-memory SQLite
 * database would otherwise get its own empty schema.
 */
class JdbcTeamStoreTest {

    @TempDir
    Path tempDir;

    private DataSource dataSource;
    private JdbcTeamStore store;
    private final List<String> log = new java.util.ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        SQLiteDataSource sqlite = new SQLiteDataSource();
        sqlite.setUrl("jdbc:sqlite:" + tempDir.resolve("test.db").toAbsolutePath());
        this.dataSource = sqlite;
        this.store = new JdbcTeamStore(dataSource, log::add);
        store.initSchema();
    }

    /**
     * Builds a team through the public snapshot API rather than the model's
     * package-private mutators - which is exactly how a {@link JdbcTeamStore}
     * sees a team, and confirms the model stays sealed against writes from
     * outside its own package.
     */
    private static Team team(String name, TeamType type, UUID leader, Map<UUID, TeamRole> members,
                             Set<UUID> allies, double balance, long points, int kothCaptures,
                             WorldPosition rally, long rallyExpiresAt) {
        return Team.fromSnapshot(new TeamSnapshot(UUID.randomUUID(), name, type, null, leader,
                1_700_000_000_000L, balance, points, kothCaptures, rally, rallyExpiresAt, members, allies));
    }

    private static Team sampleTeam(String name, UUID leader) {
        return team(name, TeamType.PLAYER, leader, Map.of(leader, TeamRole.LEADER), Set.of(),
                0.0, 0L, 0, null, 0L);
    }

    /** Returns a copy of {@code team} with one field changed, since a Team is not re-openable here. */
    private static Team renamed(Team team, String name, long points) {
        TeamSnapshot s = team.toSnapshot();
        return Team.fromSnapshot(new TeamSnapshot(s.id(), name, s.type(), s.systemZone(), s.leader(), s.createdAt(),
                s.balance(), points, s.kothCaptures(), s.rally(), s.rallyExpiresAt(), s.members(), s.allies()));
    }

    private static Team withMembers(Team team, Map<UUID, TeamRole> members) {
        TeamSnapshot s = team.toSnapshot();
        return Team.fromSnapshot(new TeamSnapshot(s.id(), s.name(), s.type(), s.systemZone(), s.leader(), s.createdAt(),
                s.balance(), s.points(), s.kothCaptures(), s.rally(), s.rallyExpiresAt(), members, s.allies()));
    }

    private static Team withAllies(Team team, Set<UUID> allies) {
        TeamSnapshot s = team.toSnapshot();
        return Team.fromSnapshot(new TeamSnapshot(s.id(), s.name(), s.type(), s.systemZone(), s.leader(), s.createdAt(),
                s.balance(), s.points(), s.kothCaptures(), s.rally(), s.rallyExpiresAt(), s.members(), allies));
    }

    @Test
    void migrationsCreateTheSchemaAndAreIdempotent() throws Exception {
        SchemaMigrator migrator = new SchemaMigrator(dataSource, log::add);

        assertEquals(latestTeamVersion(), migrator.currentVersion(TeamSchema.MODULE));
        assertEquals(0, migrator.migrate(TeamSchema.migrations()),
                "re-running applied migrations must be a no-op");
        assertEquals(latestTeamVersion(), migrator.currentVersion(TeamSchema.MODULE));
    }

    /** The version a fully migrated team schema reports - whatever the latest migration is. */
    private static int latestTeamVersion() {
        return TeamSchema.migrations().stream().mapToInt(Migration::version).max().orElseThrow();
    }

    @Test
    void anUnappliedMigrationRunsAndBumpsTheVersion() throws Exception {
        SchemaMigrator migrator = new SchemaMigrator(dataSource, log::add);
        int next = latestTeamVersion() + 1;
        Migration upcoming = Migration.of(TeamSchema.MODULE, next, "add a column",
                List.of("ALTER TABLE hcf_teams ADD COLUMN motd VARCHAR(128) NULL"));

        assertEquals(1, migrator.migrate(List.of(upcoming)));
        assertEquals(next, migrator.currentVersion(TeamSchema.MODULE));
        assertEquals(0, migrator.migrate(List.of(upcoming)));
    }

    @Test
    void aFailingMigrationRollsBackAndDoesNotRecordItsVersion() throws Exception {
        SchemaMigrator migrator = new SchemaMigrator(dataSource, log::add);
        Migration broken = Migration.of("broken", 1, "invalid sql",
                List.of("CREATE TABLE hcf_ok (id VARCHAR(36) NOT NULL PRIMARY KEY)",
                        "THIS IS NOT SQL"));

        assertThrows(SQLException.class, () -> migrator.migrate(List.of(broken)));
        assertEquals(0, migrator.currentVersion("broken"),
                "a half-applied migration must not be marked as done");
    }

    @Test
    void modulesTrackTheirSchemaVersionIndependently() throws Exception {
        SchemaMigrator migrator = new SchemaMigrator(dataSource, log::add);
        Migration otherModule = Migration.of("claim", 1, "claims table",
                List.of("CREATE TABLE IF NOT EXISTS hcf_team_claims ("
                        + "team_id VARCHAR(36) NOT NULL, world VARCHAR(64) NOT NULL, "
                        + "chunk_x INT NOT NULL, chunk_z INT NOT NULL, "
                        + "PRIMARY KEY (world, chunk_x, chunk_z))"));

        assertEquals(1, migrator.migrate(List.of(otherModule)));
        assertEquals(1, migrator.currentVersion("claim"));
        assertEquals(latestTeamVersion(), migrator.currentVersion(TeamSchema.MODULE),
                "the team version is untouched");
    }

    @Test
    void savesAndReloadsATeamWithEverythingPersisted() throws Exception {
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        Team team = team("Wizards", TeamType.PLAYER, leader,
                Map.of(leader, TeamRole.LEADER, member, TeamRole.CO_LEADER), Set.of(),
                2500.75, 88L, 1,
                new WorldPosition("world_nether", 1.5, 64.0, -20.25, 90.0f, -12.5f), 1_700_000_900_000L);

        store.save(team);
        Team loaded = single(store.loadAll());

        assertEquals(team.getId(), loaded.getId());
        assertEquals("Wizards", loaded.getName());
        assertEquals(TeamType.PLAYER, loaded.getType());
        assertEquals(leader, loaded.getLeader().orElseThrow());
        assertEquals(1_700_000_000_000L, loaded.getCreatedAt());
        assertEquals(2500.75, loaded.getBalance());
        assertEquals(88L, loaded.getPoints());
        assertEquals(1, loaded.getKothCaptures());
        assertEquals(Map.of(leader, TeamRole.LEADER, member, TeamRole.CO_LEADER), loaded.getMembers());
        assertEquals(new WorldPosition("world_nether", 1.5, 64.0, -20.25, 90.0f, -12.5f),
                loaded.getRally().orElseThrow());
        assertEquals(1_700_000_900_000L, loaded.getRallyExpiresAt());
        assertFalse(loaded.isDirty());
    }

    @Test
    void aTeamsOwnSettingsAreSavedReplacedAndDeleted() throws Exception {
        UUID leader = UUID.randomUUID();
        UUID officer = UUID.randomUUID();
        Team team = withMembers(sampleTeam("Wizards", leader), Map.of(leader, TeamRole.LEADER, officer, TeamRole.OFFICER));
        team.setPermission("kick", TeamRole.OFFICER);
        team.setPermission("claim", TeamRole.MEMBER);
        team.setJoinMode(JoinMode.OPEN);
        String description = "A description of a hundred characters, well beyond the sixty-four the column once held.";
        team.setDescription(description);
        team.setDiscord("https://discord.gg/abc123");
        store.save(team);

        Team loaded = single(store.loadAll());
        assertEquals(TeamRole.OFFICER, loaded.getRole(officer).orElseThrow());
        assertEquals(Map.of("kick", TeamRole.OFFICER, "claim", TeamRole.MEMBER), loaded.getPermissions());
        assertEquals(JoinMode.OPEN, loaded.getJoinMode().orElseThrow());
        assertEquals(description, loaded.getDescription().orElseThrow());
        assertEquals("https://discord.gg/abc123", loaded.getDiscord().orElseThrow());

        team.setPermission("claim", null);
        team.setJoinMode(null);
        team.setDescription(null);
        store.save(team);
        loaded = single(store.loadAll());
        assertEquals(Map.of("kick", TeamRole.OFFICER), loaded.getPermissions());
        assertTrue(loaded.getJoinMode().isEmpty());
        assertTrue(loaded.getDescription().isEmpty());

        store.delete(team.getId());
        try (var connection = dataSource.getConnection();
             var rows = connection.createStatement().executeQuery("SELECT COUNT(*) FROM hcf_team_settings")) {
            rows.next();
            assertEquals(0, rows.getInt(1), "a deleted team's settings must not survive it");
        }
    }

    @Test
    void savingTwiceUpdatesInPlaceInsteadOfDuplicating() throws Exception {
        Team team = sampleTeam("Wizards", UUID.randomUUID());
        store.save(team);

        store.save(renamed(team, "Warlocks", 5L));

        Team loaded = single(store.loadAll());
        assertEquals("Warlocks", loaded.getName());
        assertEquals(5L, loaded.getPoints());
    }

    @Test
    void removedMembersDisappearOnTheNextSave() throws Exception {
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        Team team = sampleTeam("Wizards", leader);
        store.save(withMembers(team, Map.of(leader, TeamRole.LEADER, member, TeamRole.MEMBER)));

        store.save(withMembers(team, Map.of(leader, TeamRole.LEADER)));

        assertEquals(Set.of(leader), single(store.loadAll()).getMemberIds());
    }

    @Test
    void alliancesSurviveARoundTrip() throws Exception {
        Team wizards = sampleTeam("Wizards", UUID.randomUUID());
        Team warlocks = sampleTeam("Warlocks", UUID.randomUUID());
        store.save(withAllies(wizards, Set.of(warlocks.getId())));
        store.save(withAllies(warlocks, Set.of(wizards.getId())));

        Collection<Team> loaded = store.loadAll();
        assertEquals(2, loaded.size());
        Team loadedWizards = loaded.stream()
                .filter(t -> t.getName().equals("Wizards")).findFirst().orElseThrow();
        assertEquals(Set.of(warlocks.getId()), loadedWizards.getAllies());
    }

    @Test
    void aSystemTeamRoundTripsWithNoLeaderAndNoMembers() throws Exception {
        Team spawn = team("Spawn", TeamType.SYSTEM, null, Map.of(), Set.of(), 0.0, 0L, 0, null, 0L);
        store.save(spawn);

        Team loaded = single(store.loadAll());
        assertEquals(TeamType.SYSTEM, loaded.getType());
        assertTrue(loaded.getLeader().isEmpty());
        assertEquals(0, loaded.getMemberCount());
        assertTrue(loaded.getRally().isEmpty(), "a null rally must come back as absent, not as world '0'");
    }

    @Test
    void deletingRemovesTheTeamItsMembersAndAlliancesPointingAtIt() throws Exception {
        Team wizards = sampleTeam("Wizards", UUID.randomUUID());
        Team warlocks = sampleTeam("Warlocks", UUID.randomUUID());
        store.save(withAllies(wizards, Set.of(warlocks.getId())));
        store.save(withAllies(warlocks, Set.of(wizards.getId())));

        store.delete(wizards.getId());

        Team remaining = single(store.loadAll());
        assertEquals("Warlocks", remaining.getName());
        assertTrue(remaining.getAllies().isEmpty(), "a dangling alliance row must not survive the delete");
    }

    @Test
    void theUniqueNameConstraintIsEnforcedByTheDatabase() throws Exception {
        store.save(sampleTeam("Wizards", UUID.randomUUID()));

        assertThrows(SQLException.class, () -> store.save(sampleTeam("Wizards", UUID.randomUUID())),
                "two teams must never share a name, even if a caller skips the manager");
    }

    @Test
    void anUnknownPersistedRoleFallsBackToMemberInsteadOfDroppingThePlayer() throws Exception {
        UUID leader = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        Team team = sampleTeam("Wizards", leader);
        store.save(team);

        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "INSERT INTO hcf_team_members (player_uuid, team_id, role) VALUES (?, ?, 'EMPEROR')")) {
            statement.setString(1, stranger.toString());
            statement.setString(2, team.getId().toString());
            statement.executeUpdate();
        }

        Team loaded = single(store.loadAll());
        assertEquals(TeamRole.MEMBER, loaded.getRole(stranger).orElseThrow());
        assertTrue(log.stream().anyMatch(line -> line.contains("Unknown team role")),
                "the fallback must be reported, not silent");
    }

    @Test
    void anUnknownPersistedZoneFallsBackToASafeZone() throws Exception {
        Team spawn = Team.fromSnapshot(new TeamSnapshot(UUID.randomUUID(), "Spawn", TeamType.SYSTEM,
                SystemZone.SAFE, null, 1_700_000_000_000L, 0.0, 0L, 0, null, 0L, Map.of(), Set.of()));
        store.save(spawn);
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE hcf_teams SET system_zone = 'ARENA'");
        }

        Team loaded = single(store.loadAll());
        assertEquals(Optional.of(SystemZone.SAFE), loaded.getSystemZone());
        assertTrue(loaded.isSafeZone(), "the fallback the log announces must be the one applied");
        assertTrue(log.stream().anyMatch(line -> line.contains("Unknown system zone")));
    }

    @Test
    void loadAllOnAFreshDatabaseReturnsNothing() throws Exception {
        assertTrue(store.loadAll().isEmpty());
    }

    private static Team single(Collection<Team> teams) {
        assertEquals(1, teams.size(), "expected exactly one team, got " + teams.size());
        Team team = teams.iterator().next();
        assertNotNull(team);
        return team;
    }

    @Test
    void aSystemTeamKeepsItsZoneKindAcrossARestart() throws Exception {
        Team warzone = Team.fromSnapshot(new TeamSnapshot(UUID.randomUUID(), "Warzone", TeamType.SYSTEM,
                SystemZone.COMBAT, null, 1_700_000_000_000L, 0.0, 0L, 0, null, 0L, Map.of(), Set.of()));
        Team player = sampleTeam("Wizards", UUID.randomUUID());
        store.save(warzone);
        store.save(player);

        Map<String, Team> loaded = new java.util.HashMap<>();
        for (Team team : store.loadAll()) {
            loaded.put(team.getName(), team);
        }

        assertEquals(Optional.of(SystemZone.COMBAT), loaded.get("Warzone").getSystemZone());
        assertFalse(loaded.get("Warzone").isSafeZone());
        assertEquals(Optional.empty(), loaded.get("Wizards").getSystemZone(), "a player team has no zone kind");
    }

    @Test
    void aSystemTeamStoredBeforeZoneKindsExistedComesBackAsASafeZone() throws Exception {
        // A database brought only to team v1, holding a system team - exactly what a
        // server running the previous version has on disk.
        SQLiteDataSource legacy = new SQLiteDataSource();
        legacy.setUrl("jdbc:sqlite:" + tempDir.resolve("legacy.db").toAbsolutePath());
        new SchemaMigrator(legacy, log::add).migrate(List.of(TeamSchema.migrations().get(0)));
        try (java.sql.Connection connection = legacy.getConnection();
             java.sql.Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO hcf_teams (id, name, type, created_at) VALUES ('"
                    + UUID.randomUUID() + "', 'Spawn', 'SYSTEM', 0)");
        }

        JdbcTeamStore upgraded = new JdbcTeamStore(legacy, log::add);
        upgraded.initSchema();
        Team spawn = upgraded.loadAll().iterator().next();

        assertEquals(Optional.of(SystemZone.SAFE), spawn.getSystemZone(),
                "every system team was a safe zone before the choice existed; the upgrade must not change that");
        assertTrue(spawn.isSafeZone());
    }
}
