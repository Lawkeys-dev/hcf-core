package com.lawkeys.hcfcore.database;

import com.lawkeys.hcfcore.database.dao.JdbcStaffBanStore;
import com.lawkeys.hcfcore.staff.StaffBan;
import com.lawkeys.hcfcore.staff.StaffBans;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Moderation bans against a real SQLite database. */
class JdbcStaffBanStoreTest {

    @TempDir
    Path tempDir;

    private SQLiteDataSource sqlite;
    private JdbcStaffBanStore store;
    private final List<String> log = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        sqlite = new SQLiteDataSource();
        sqlite.setUrl("jdbc:sqlite:" + tempDir.resolve("test.db").toAbsolutePath());
        store = new JdbcStaffBanStore(sqlite, log::add);
        store.initSchema();
    }

    @Test
    void aBanSurvivesTheRoundTrip() throws Exception {
        UUID alice = UUID.randomUUID();
        store.save(new StaffBan(alice, "logged out while frozen", "SomeStaff", 1_700L));

        Map<UUID, StaffBan> loaded = store.loadAll();
        assertEquals(1, loaded.size());
        StaffBan ban = loaded.get(alice);
        assertEquals("logged out while frozen", ban.reason());
        assertEquals("SomeStaff", ban.bannedBy());
        assertEquals(1_700L, ban.bannedAt());
    }

    @Test
    void aLiftedBanIsGone() throws Exception {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        store.save(new StaffBan(alice, "a", "S", 1L));
        store.save(new StaffBan(bob, "b", "S", 2L));

        store.delete(alice);
        assertEquals(List.of(bob), List.copyOf(store.loadAll().keySet()));
    }

    /**
     * The point of the table: somebody who ran from a check is still out after a
     * restart, and staff lifting it is what lets them back in.
     */
    @Test
    void aBanOutlivesTheServerUntilStaffLiftIt() throws Exception {
        UUID alice = UUID.randomUUID();

        StaffBans before = new StaffBans(store);
        before.loadAll();
        assertTrue(before.ban(alice, "logged out while frozen", "SomeStaff", 500L));
        before.flush();

        StaffBans after = new StaffBans(new JdbcStaffBanStore(sqlite, log::add));
        after.loadAll();
        assertTrue(after.isBanned(alice));
        assertEquals("SomeStaff", after.get(alice).orElseThrow().bannedBy());

        assertTrue(after.lift(alice));
        after.flush();

        StaffBans later = new StaffBans(new JdbcStaffBanStore(sqlite, log::add));
        later.loadAll();
        assertFalse(later.isBanned(alice));
    }

    @Test
    void theSchemaCanBeAppliedTwice() throws Exception {
        store.initSchema();
        assertTrue(store.loadAll().isEmpty());
    }
}
