package com.lawkeys.hcfcore.database;

import com.lawkeys.hcfcore.database.dao.JdbcLastInventoryStore;
import com.lawkeys.hcfcore.staff.DeathSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The death archive against a real SQLite database.
 *
 * <p>The prune is the reason this test matters most: it is the one statement here
 * that cannot be checked by reading it, and getting it wrong either keeps
 * everything for ever or throws away the death somebody is asking about.
 */
class JdbcLastInventoryStoreTest {

    @TempDir
    Path tempDir;

    private JdbcLastInventoryStore store;
    private final List<String> log = new ArrayList<>();
    private UUID alice;
    private UUID bob;

    @BeforeEach
    void setUp() throws Exception {
        SQLiteDataSource sqlite = new SQLiteDataSource();
        sqlite.setUrl("jdbc:sqlite:" + tempDir.resolve("test.db").toAbsolutePath());
        store = new JdbcLastInventoryStore(sqlite, log::add);
        store.initSchema();
        alice = UUID.randomUUID();
        bob = UUID.randomUUID();
    }

    @Test
    void aDeathSurvivesTheRoundTripByteForByte() throws Exception {
        byte[] contents = {0, 1, -1, 127, -128, 42};
        store.save(new DeathSnapshot(alice, 1_000L, contents), 3);

        List<DeathSnapshot> recent = store.recent(alice, 3);
        assertEquals(1, recent.size());
        assertEquals(1_000L, recent.get(0).diedAt());
        assertArrayEquals(contents, recent.get(0).contents());
    }

    @Test
    void deathsComeBackNewestFirst() throws Exception {
        store.save(new DeathSnapshot(alice, 1_000L, new byte[] {1}), 5);
        store.save(new DeathSnapshot(alice, 3_000L, new byte[] {3}), 5);
        store.save(new DeathSnapshot(alice, 2_000L, new byte[] {2}), 5);

        List<DeathSnapshot> recent = store.recent(alice, 5);
        assertEquals(List.of(3_000L, 2_000L, 1_000L),
                recent.stream().map(DeathSnapshot::diedAt).toList());
    }

    /** The whole point of the `keep` argument: the table must not grow for ever. */
    @Test
    void onlyTheNewestFewAreKept() throws Exception {
        for (int i = 1; i <= 6; i++) {
            store.save(new DeathSnapshot(alice, i * 1_000L, new byte[] {(byte) i}), 3);
        }
        List<DeathSnapshot> recent = store.recent(alice, 10);
        assertEquals(3, recent.size());
        assertEquals(List.of(6_000L, 5_000L, 4_000L),
                recent.stream().map(DeathSnapshot::diedAt).toList());
    }

    /** Pruning one player must not touch another's archive. */
    @Test
    void pruningIsPerPlayer() throws Exception {
        for (int i = 1; i <= 5; i++) {
            store.save(new DeathSnapshot(bob, i * 1_000L, new byte[] {(byte) i}), 2);
        }
        store.save(new DeathSnapshot(alice, 9_000L, new byte[] {9}), 2);

        assertEquals(2, store.recent(bob, 10).size());
        assertEquals(1, store.recent(alice, 10).size());
        assertEquals(9_000L, store.recent(alice, 10).get(0).diedAt());
    }

    @Test
    void keepingOneLeavesOnlyTheLatest() throws Exception {
        store.save(new DeathSnapshot(alice, 1_000L, new byte[] {1}), 1);
        store.save(new DeathSnapshot(alice, 2_000L, new byte[] {2}), 1);

        List<DeathSnapshot> recent = store.recent(alice, 10);
        assertEquals(1, recent.size());
        assertArrayEquals(new byte[] {2}, recent.get(0).contents());
    }

    /** A nonsensical keep must not empty the archive it was asked to protect. */
    @Test
    void aKeepOfZeroStillKeepsTheLatest() throws Exception {
        store.save(new DeathSnapshot(alice, 1_000L, new byte[] {1}), 0);
        assertEquals(1, store.recent(alice, 10).size());
    }

    @Test
    void anInventoryLargerThanAMysqlBlobStillFits() throws Exception {
        byte[] contents = new byte[200 * 1024];
        new Random(11).nextBytes(contents);
        store.save(new DeathSnapshot(alice, 1_000L, contents), 3);

        assertArrayEquals(contents, store.recent(alice, 1).get(0).contents());
    }

    @Test
    void aPlayerWhoNeverDiedHasAnEmptyArchive() throws Exception {
        assertTrue(store.recent(alice, 3).isEmpty());
    }

    @Test
    void theSchemaCanBeAppliedTwice() throws Exception {
        store.initSchema();
        assertTrue(store.recent(alice, 3).isEmpty());
    }
}
