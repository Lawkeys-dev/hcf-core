package com.lawkeys.hcfcore.database;

import com.lawkeys.hcfcore.database.dao.JdbcLastInventoryStore;
import com.lawkeys.hcfcore.database.dao.JdbcStaffBanStore;
import com.lawkeys.hcfcore.database.dao.JdbcStaffStashStore;
import com.lawkeys.hcfcore.database.migration.SchemaMigrator;
import com.lawkeys.hcfcore.staff.StaffSchema;
import com.lawkeys.hcfcore.staff.StaffStashes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The staff stash table against a real SQLite database - the statements are the
 * ones MySQL runs too (see {@code JdbcTeamStoreTest} for why a file and not
 * {@code :memory:}).
 */
class JdbcStaffStashStoreTest {

    @TempDir
    Path tempDir;

    private SQLiteDataSource sqlite;
    private JdbcStaffStashStore store;
    private final List<String> log = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        sqlite = new SQLiteDataSource();
        sqlite.setUrl("jdbc:sqlite:" + tempDir.resolve("test.db").toAbsolutePath());
        store = new JdbcStaffStashStore(sqlite, log::add);
        store.initSchema();
    }

    @Test
    void anInventorySurvivesTheRoundTripByteForByte() throws Exception {
        UUID alice = UUID.randomUUID();
        byte[] contents = {0, 1, -1, 127, -128, 42};
        store.save(alice, contents);

        Map<UUID, byte[]> loaded = store.loadAll();
        assertEquals(1, loaded.size());
        assertArrayEquals(contents, loaded.get(alice));
    }

    @Test
    void anInventoryLargerThanAMysqlBlobStillFits() throws Exception {
        // 200 KB: past BLOB's 65,535 bytes, which is why the column is a MEDIUMBLOB.
        byte[] contents = new byte[200 * 1024];
        new Random(7).nextBytes(contents);
        UUID alice = UUID.randomUUID();
        store.save(alice, contents);

        assertArrayEquals(contents, store.loadAll().get(alice));
    }

    @Test
    void savingAgainReplacesTheRow() throws Exception {
        UUID alice = UUID.randomUUID();
        store.save(alice, new byte[] {1});
        store.save(alice, new byte[] {2, 2});

        assertArrayEquals(new byte[] {2, 2}, store.loadAll().get(alice));
    }

    @Test
    void aDeletedStashIsGone() throws Exception {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        store.save(alice, new byte[] {1});
        store.save(bob, new byte[] {2});

        store.delete(alice);
        assertEquals(List.of(bob), List.copyOf(store.loadAll().keySet()));
    }

    @Test
    void theSchemaCanBeAppliedTwice() throws Exception {
        store.initSchema();
        assertTrue(store.loadAll().isEmpty());
    }

    /**
     * A server that has been running the previous version has only the stash table
     * on disk. Starting the new one must add the ban and archive tables without
     * touching what is already there - the upgrade path every existing install
     * takes, and the one nobody notices until it fails.
     */
    @Test
    void aDatabaseAtVersionOneGainsTheNewTables() throws Exception {
        SQLiteDataSource legacy = new SQLiteDataSource();
        legacy.setUrl("jdbc:sqlite:" + tempDir.resolve("legacy.db").toAbsolutePath());
        new SchemaMigrator(legacy, log::add).migrate(List.of(StaffSchema.migrations().get(0)));

        UUID alice = UUID.randomUUID();
        JdbcStaffStashStore old = new JdbcStaffStashStore(legacy, log::add);
        old.save(alice, new byte[] {4, 5, 6});

        // Starting the current version over that database.
        JdbcStaffStashStore upgraded = new JdbcStaffStashStore(legacy, log::add);
        upgraded.initSchema();
        assertArrayEquals(new byte[] {4, 5, 6}, upgraded.loadAll().get(alice),
                "the held inventory must survive the upgrade");

        // The tables migrations 2 and 3 add are now usable.
        JdbcStaffBanStore bans = new JdbcStaffBanStore(legacy, log::add);
        bans.save(new com.lawkeys.hcfcore.staff.StaffBan(alice, "r", "S", 1L));
        assertTrue(bans.loadAll().containsKey(alice));

        JdbcLastInventoryStore archive = new JdbcLastInventoryStore(legacy, log::add);
        archive.save(new com.lawkeys.hcfcore.staff.DeathSnapshot(alice, 1L, new byte[] {7}), 3);
        assertEquals(1, archive.recent(alice, 3).size());
    }

    /**
     * The whole point of the table: a staff member whose server died mid-staff-mode
     * still gets their items back. Written by one manager, read by the next one to
     * start up.
     */
    @Test
    void aHeldInventoryOutlivesTheServer() throws Exception {
        UUID alice = UUID.randomUUID();
        byte[] contents = {9, 8, 7, 6};

        StaffStashes before = new StaffStashes(store);
        before.loadAll();
        assertTrue(before.put(alice, contents));
        before.flush();

        // A new process, reading the same database.
        StaffStashes after = new StaffStashes(new JdbcStaffStashStore(sqlite, log::add));
        after.loadAll();
        assertTrue(after.has(alice));
        assertArrayEquals(contents, after.get(alice).orElseThrow());

        // And once given back, the row goes with it.
        after.remove(alice);
        after.flush();

        StaffStashes later = new StaffStashes(new JdbcStaffStashStore(sqlite, log::add));
        later.loadAll();
        assertFalse(later.has(alice));
    }
}
