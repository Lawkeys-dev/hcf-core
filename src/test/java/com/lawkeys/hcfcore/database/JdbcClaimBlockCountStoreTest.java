package com.lawkeys.hcfcore.database;

import com.lawkeys.hcfcore.database.dao.JdbcClaimBlockCountStore;
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
        store.save(chunk, Map.of("HOPPER", 12, "SPAWNER", 2));
        assertEquals(Map.of(chunk, Map.of("HOPPER", 12, "SPAWNER", 2)), store.loadAll());

        store.save(chunk, Map.of("HOPPER", 11));
        assertEquals(Map.of(chunk, Map.of("HOPPER", 11)), store.loadAll());

        store.save(chunk, Map.of());
        assertEquals(Map.of(), store.loadAll(), "an empty chunk leaves no rows");
    }
}
