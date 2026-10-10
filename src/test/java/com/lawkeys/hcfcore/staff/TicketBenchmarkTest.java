package com.lawkeys.hcfcore.staff;

import com.lawkeys.hcfcore.database.dao.JdbcTicketStore;
import com.lawkeys.hcfcore.staff.ticket.TicketManager;
import com.lawkeys.hcfcore.staff.ticket.TicketStatus;
import com.lawkeys.hcfcore.staff.ticket.TicketType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TicketBenchmarkTest {

    @TempDir
    Path tempDir;

    @Test
    void benchmarkFlushPerformance() throws Exception {
        SQLiteDataSource sqlite = new SQLiteDataSource();
        sqlite.setUrl("jdbc:sqlite:" + tempDir.resolve("benchmark.db").toAbsolutePath());

        JdbcTicketStore store = new JdbcTicketStore(sqlite, msg -> {});
        store.initSchema();

        TicketManager ticketManager = new TicketManager(store, () -> 0L);
        ticketManager.loadAll();

        int ticketCount = 1000;
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        // 1. Open tickets (Insert phase)
        for (int i = 0; i < ticketCount; i++) {
            ticketManager.open(TicketType.REPORT, alice, "Alice", bob, "Bob", "Report message " + i);
        }

        long startTimeInserts = System.nanoTime();
        int flushedInserts = ticketManager.flush();
        long durationInsertsMs = (System.nanoTime() - startTimeInserts) / 1_000_000;

        assertEquals(ticketCount, flushedInserts);
        System.out.println("BENCHMARK_RESULT_INSERTS_MS=" + durationInsertsMs);

        // 2. Modify tickets (Update phase)
        for (long id = 1; id <= ticketCount; id++) {
            if (id % 2 == 0) {
                ticketManager.claim(id, "Staff1");
            } else {
                ticketManager.close(id, "Staff2");
            }
        }

        long startTimeUpdates = System.nanoTime();
        int flushedUpdates = ticketManager.flush();
        long durationUpdatesMs = (System.nanoTime() - startTimeUpdates) / 1_000_000;

        assertEquals(ticketCount, flushedUpdates);
        System.out.println("BENCHMARK_RESULT_UPDATES_MS=" + durationUpdatesMs);
        System.out.println("BENCHMARK_RESULT_TOTAL_MS=" + (durationInsertsMs + durationUpdatesMs));
    }
}
