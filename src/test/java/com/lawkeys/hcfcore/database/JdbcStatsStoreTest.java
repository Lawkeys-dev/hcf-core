package com.lawkeys.hcfcore.database;

import com.lawkeys.hcfcore.database.dao.JdbcStatsStore;
import com.lawkeys.hcfcore.stats.PlayerStats;
import com.lawkeys.hcfcore.stats.StatsManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Player statistics against a real SQLite database. */
class JdbcStatsStoreTest {

    @TempDir
    Path tempDir;

    private SQLiteDataSource sqlite;
    private JdbcStatsStore store;
    private final List<String> log = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        sqlite = new SQLiteDataSource();
        sqlite.setUrl("jdbc:sqlite:" + tempDir.resolve("test.db").toAbsolutePath());
        store = new JdbcStatsStore(sqlite, log::add);
        store.initSchema();
    }

    @Test
    void statsSurviveTheRoundTrip() throws Exception {
        UUID alice = UUID.randomUUID();
        store.save(PlayerStats.restore(alice, "Alice", 12, 3, 4, 9, 3_600L, 100L, 200L));

        PlayerStats loaded = store.loadAll().iterator().next();
        assertEquals(alice, loaded.getPlayerId());
        assertEquals("Alice", loaded.getName());
        assertEquals(12, loaded.getKills());
        assertEquals(3, loaded.getDeaths());
        assertEquals(4, loaded.getKillstreak());
        assertEquals(9, loaded.getHighestKillstreak());
        assertEquals(3_600L, loaded.storedPlaytimeSeconds());
        assertEquals(100L, loaded.getFirstSeen());
        assertEquals(200L, loaded.getLastSeen());
    }

    @Test
    void savingAgainReplacesTheRow() throws Exception {
        UUID alice = UUID.randomUUID();
        store.save(PlayerStats.restore(alice, "Alice", 1, 0, 1, 1, 0L, 0L, 0L));
        store.save(PlayerStats.restore(alice, "Alice", 5, 2, 0, 3, 60L, 0L, 0L));

        assertEquals(1, store.loadAll().size());
        assertEquals(5, store.loadAll().iterator().next().getKills());
    }

    /**
     * The trap this schema could have fallen into: playtime is banked when a session
     * ends, so writing the live total mid-session would count that session again at
     * the end. The stored figure is what goes to the database.
     */
    @Test
    void aSessionInProgressIsNotWrittenTwice() throws Exception {
        AtomicLong now = new AtomicLong(1_000_000L);
        UUID alice = UUID.randomUUID();

        StatsManager manager = new StatsManager(store, now::get);
        manager.loadAll();
        manager.beginSession(alice, "Alice");
        now.addAndGet(60_000L);
        manager.flush();                    // saved mid-session

        now.addAndGet(60_000L);
        manager.endAllSessions();           // 120s total
        manager.flush();

        StatsManager reloaded = new StatsManager(new JdbcStatsStore(sqlite, log::add), now::get);
        reloaded.loadAll();
        assertEquals(120L, reloaded.find(alice).orElseThrow().storedPlaytimeSeconds(),
                "the session must be counted once, not once per flush");
    }

    @Test
    void aWholeCareerOutlivesTheServer() throws Exception {
        AtomicLong now = new AtomicLong(1_000_000L);
        UUID alice = UUID.randomUUID();

        StatsManager before = new StatsManager(store, now::get);
        before.loadAll();
        before.beginSession(alice, "Alice");
        before.recordKill(alice, "Alice");
        before.recordKill(alice, "Alice");
        before.recordDeath(alice, "Alice");
        now.addAndGet(30_000L);
        before.endAllSessions();
        before.flush();

        StatsManager after = new StatsManager(new JdbcStatsStore(sqlite, log::add), now::get);
        after.loadAll();
        PlayerStats row = after.find(alice).orElseThrow();
        assertEquals(2, row.getKills());
        assertEquals(1, row.getDeaths());
        assertEquals(2, row.getHighestKillstreak());
        assertEquals(30L, row.storedPlaytimeSeconds());
    }

    @Test
    void theSchemaCanBeAppliedTwice() throws Exception {
        store.initSchema();
        assertTrue(store.loadAll().isEmpty());
    }
}
