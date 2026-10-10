package com.lawkeys.hcfcore.database;

import com.lawkeys.hcfcore.database.dao.JdbcStaffStashStore;
import com.lawkeys.hcfcore.staff.StaffStashes;
import com.lawkeys.hcfcore.staff.StaffStashStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StaffStashesBenchmarkTest {

    @TempDir
    Path tempDir;

    private SQLiteDataSource sqlite;
    private JdbcStaffStashStore store;

    @BeforeEach
    void setUp() throws Exception {
        sqlite = new SQLiteDataSource();
        sqlite.setUrl("jdbc:sqlite:" + tempDir.resolve("bench.db").toAbsolutePath());
        store = new JdbcStaffStashStore(sqlite, msg -> {});
        store.initSchema();
    }

    @Test
    void benchmarkFlushPerformance() throws Exception {
        int count = 200;
        StaffStashes stashes = new StaffStashes(store);
        byte[] dummy = new byte[] {1, 2, 3, 4, 5};

        for (int i = 0; i < count; i++) {
            stashes.put(UUID.randomUUID(), dummy);
        }

        long start = System.nanoTime();
        int written = stashes.flush();
        long durationMs = (System.nanoTime() - start) / 1_000_000;

        assertEquals(count, written);

        // Also test individual fallback performance vs batch if using non-batch store
        StaffStashes unbatchedStashes = new StaffStashes(new UnbatchedStore(store));
        for (int i = 0; i < count; i++) {
            unbatchedStashes.put(UUID.randomUUID(), dummy);
        }

        long unbatchedStart = System.nanoTime();
        int unbatchedWritten = unbatchedStashes.flush();
        long unbatchedDurationMs = (System.nanoTime() - unbatchedStart) / 1_000_000;

        assertEquals(count, unbatchedWritten);

        System.out.printf("BENCHMARK RESULT: Batch flush of %d stashes took %d ms vs Unbatched %d ms (%.2fx speedup)%n",
                count, durationMs, unbatchedDurationMs, (double) unbatchedDurationMs / Math.max(1, durationMs));
    }

    private static class UnbatchedStore implements StaffStashStore {
        private final StaffStashStore delegate;

        UnbatchedStore(StaffStashStore delegate) {
            this.delegate = delegate;
        }

        @Override
        public void initSchema() throws Exception {
            delegate.initSchema();
        }

        @Override
        public java.util.Map<UUID, byte[]> loadAll() throws Exception {
            return delegate.loadAll();
        }

        @Override
        public void save(UUID playerId, byte[] contents) throws Exception {
            delegate.save(playerId, contents);
        }

        @Override
        public void delete(UUID playerId) throws Exception {
            delegate.delete(playerId);
        }
    }
}
