package com.lawkeys.hcfcore.events.king;

import com.lawkeys.hcfcore.database.dao.JdbcKingStashStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KingStashesBenchmarkTest {

    @TempDir
    Path tempDir;

    private JdbcKingStashStore store;
    private KingStashes stashes;

    @BeforeEach
    void setUp() throws Exception {
        SQLiteDataSource sqlite = new SQLiteDataSource();
        sqlite.setUrl("jdbc:sqlite:" + tempDir.resolve("benchmark.db").toAbsolutePath());
        store = new JdbcKingStashStore(sqlite, msg -> {});
        stashes = new KingStashes(store);
        stashes.loadAll();
    }

    @Test
    void benchmarkFlushPerformance() throws Exception {
        int count = 200;
        List<UUID> players = new ArrayList<>();
        byte[] dummyData = new byte[1024];

        for (int i = 0; i < count; i++) {
            UUID id = UUID.randomUUID();
            players.add(id);
            stashes.put(id, dummyData);
        }

        long startSave = System.nanoTime();
        int written = stashes.flush();
        long saveDurationMs = (System.nanoTime() - startSave) / 1_000_000;
        assertEquals(count, written);
        System.out.printf("BENCHMARK: Save %d stashes took %d ms%n", count, saveDurationMs);

        // Now remove half of them and update the other half
        for (int i = 0; i < count / 2; i++) {
            stashes.remove(players.get(i));
        }
        for (int i = count / 2; i < count; i++) {
            stashes.remove(players.get(i));
            stashes.put(players.get(i), new byte[]{1, 2, 3});
        }

        long startFlush = System.nanoTime();
        int flushed = stashes.flush();
        long flushDurationMs = (System.nanoTime() - startFlush) / 1_000_000;
        assertEquals(count, flushed);
        System.out.printf("BENCHMARK: Flush (delete %d + update %d) took %d ms%n", count / 2, count / 2, flushDurationMs);
    }
}
