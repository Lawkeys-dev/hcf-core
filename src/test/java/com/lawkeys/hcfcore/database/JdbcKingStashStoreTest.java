package com.lawkeys.hcfcore.database;

import com.lawkeys.hcfcore.database.dao.JdbcKingStashStore;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The King stash table against a real SQLite database - the statements are the
 * ones MySQL runs too (see {@code JdbcTeamStoreTest} for why a file and not
 * {@code :memory:}).
 */
class JdbcKingStashStoreTest {

    @TempDir
    Path tempDir;

    private JdbcKingStashStore store;
    private final List<String> log = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        SQLiteDataSource sqlite = new SQLiteDataSource();
        sqlite.setUrl("jdbc:sqlite:" + tempDir.resolve("test.db").toAbsolutePath());
        store = new JdbcKingStashStore(sqlite, log::add);
        store.initSchema();
    }

    @Test
    void aStashSurvivesTheRoundTripByteForByte() throws Exception {
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
        new Random(3).nextBytes(contents);
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
}
