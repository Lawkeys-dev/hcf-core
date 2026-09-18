package com.lawkeys.hcfcore.database;

import com.lawkeys.hcfcore.claim.Claim;
import com.lawkeys.hcfcore.claim.HomeType;
import com.lawkeys.hcfcore.claim.TeamHome;
import com.lawkeys.hcfcore.database.dao.JdbcClaimStore;
import com.lawkeys.hcfcore.util.ChunkPosition;
import com.lawkeys.hcfcore.util.WorldPosition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Claims and team homes against a real SQLite database. */
class JdbcClaimStoreTest {

    @TempDir
    Path tempDir;

    private SQLiteDataSource sqlite;
    private JdbcClaimStore store;
    private final List<String> log = new ArrayList<>();
    private final UUID wizards = UUID.randomUUID();
    private final UUID warlocks = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        sqlite = new SQLiteDataSource();
        sqlite.setUrl("jdbc:sqlite:" + tempDir.resolve("test.db").toAbsolutePath());
        store = new JdbcClaimStore(sqlite, log::add);
        store.initSchema();
    }

    private Claim claim(UUID team, int x, int z) {
        return new Claim(team, new ChunkPosition("world", x, z), 1_700_000_000_000L);
    }

    private Set<Claim> claimsOf(UUID team) throws Exception {
        return store.loadClaims().stream().filter(claim -> claim.teamId().equals(team)).collect(Collectors.toSet());
    }

    @Test
    void aTeamsClaimsAreReplacedWholeAndLeaveOtherTeamsAlone() throws Exception {
        store.saveClaims(wizards, List.of(claim(wizards, 0, 0), claim(wizards, 0, 1)));
        store.saveClaims(warlocks, List.of(claim(warlocks, 5, -3)));
        assertEquals(Set.of(claim(wizards, 0, 0), claim(wizards, 0, 1)), claimsOf(wizards));

        store.saveClaims(wizards, List.of(claim(wizards, 0, 1), claim(wizards, -1, 1)));
        assertEquals(Set.of(claim(wizards, 0, 1), claim(wizards, -1, 1)), claimsOf(wizards));
        assertEquals(Set.of(claim(warlocks, 5, -3)), claimsOf(warlocks));

        store.saveClaims(wizards, List.of());
        assertEquals(Set.of(), claimsOf(wizards));
    }

    @Test
    void aHomeIsWrittenThenMovedInPlace() throws Exception {
        TeamHome hq = new TeamHome(wizards, HomeType.HQ, new WorldPosition("world", 1.5, 64, -2.5, 90f, -10f), 10L);
        store.saveHome(hq);
        TeamHome moved = new TeamHome(wizards, HomeType.HQ, new WorldPosition("world_nether", 8, 70, 8, 0f, 0f), 20L);
        store.saveHome(moved);
        TeamHome base = new TeamHome(wizards, HomeType.BASE, new WorldPosition("world", 100, 60, 100, 0f, 0f), 30L);
        store.saveHome(base);

        assertEquals(Set.of(moved, base), Set.copyOf(store.loadHomes()));

        store.deleteHome(wizards, HomeType.BASE);
        assertEquals(List.of(moved), List.copyOf(store.loadHomes()));
    }

    @Test
    void deletingATeamTakesItsClaimsAndHomes() throws Exception {
        store.saveClaims(wizards, List.of(claim(wizards, 0, 0)));
        store.saveHome(new TeamHome(wizards, HomeType.HQ, new WorldPosition("world", 0, 64, 0, 0f, 0f), 1L));
        store.saveClaims(warlocks, List.of(claim(warlocks, 9, 9)));

        store.deleteTeam(wizards);

        assertEquals(Set.of(claim(warlocks, 9, 9)), Set.copyOf(store.loadClaims()));
        assertEquals(List.of(), List.copyOf(store.loadHomes()));
    }

    @Test
    void aHomeOfAnUnknownTypeIsSkippedAndReported() throws Exception {
        store.saveHome(new TeamHome(wizards, HomeType.HQ, new WorldPosition("world", 0, 64, 0, 0f, 0f), 1L));
        try (Connection connection = sqlite.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE hcf_team_homes SET home_type = 'OUTPOST'");
        }

        assertEquals(List.of(), List.copyOf(store.loadHomes()));
        assertTrue(log.stream().anyMatch(line -> line.contains("Unknown team home type 'OUTPOST'")));
    }
}
