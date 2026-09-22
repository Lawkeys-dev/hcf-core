package com.lawkeys.hcfcore.database;

import com.lawkeys.hcfcore.database.dao.JdbcClaimBlockCountStore;
import com.lawkeys.hcfcore.limiter.ClaimCell;
import com.lawkeys.hcfcore.util.ChunkPosition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Block counts per claimed chunk against a real SQLite database. */
class JdbcClaimBlockCountStoreTest {

    @TempDir
    Path tempDir;

    private final List<String> log = new ArrayList<>();

    @Test
    void countsSurviveTheRoundTripAndAreReplacedWhole() throws Exception {
        SQLiteDataSource sqlite = new SQLiteDataSource();
        sqlite.setUrl("jdbc:sqlite:" + tempDir.resolve("test.db").toAbsolutePath());
        JdbcClaimBlockCountStore store = new JdbcClaimBlockCountStore(sqlite, log::add);
        store.initSchema();
        store.initSchema();

        ChunkPosition chunk = new ChunkPosition("world", -3, 7);
        ClaimCell wizards = new ClaimCell(chunk, java.util.UUID.randomUUID());
        ClaimCell warlocks = new ClaimCell(chunk, java.util.UUID.randomUUID());
        store.save(wizards, Map.of("HOPPER", 12, "SPAWNER", 2));
        store.save(warlocks, Map.of("HOPPER", 3));
        assertEquals(Map.of(wizards, Map.of("HOPPER", 12, "SPAWNER", 2), warlocks, Map.of("HOPPER", 3)),
                store.loadAll(), "two teams sharing a chunk are counted apart");

        store.save(wizards, Map.of("HOPPER", 11));
        assertEquals(Map.of(wizards, Map.of("HOPPER", 11), warlocks, Map.of("HOPPER", 3)), store.loadAll(),
                "one team's part is replaced, the other's left alone");

        store.save(wizards, Map.of());
        store.save(warlocks, Map.of());
        assertEquals(Map.of(), store.loadAll(), "an empty cell leaves no rows");
    }
}
