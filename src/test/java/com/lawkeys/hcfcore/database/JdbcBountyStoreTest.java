package com.lawkeys.hcfcore.database;

import com.lawkeys.hcfcore.database.dao.JdbcBountyStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class JdbcBountyStoreTest {

    @TempDir
    Path tempDir;

    private JdbcBountyStore store;
    private final List<String> log = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        SQLiteDataSource sqlite = new SQLiteDataSource();
        sqlite.setUrl("jdbc:sqlite:" + tempDir.resolve("test.db").toAbsolutePath());
        store = new JdbcBountyStore(sqlite, log::add);
        store.initSchema();
    }

    @Test
    void aBountyIsWrittenUpdatedAndRemoved() throws Exception {
        UUID steve = UUID.randomUUID();
        store.save(steve, 150.0);
        assertEquals(150.0, store.loadAll().get(steve));
        store.save(steve, 400.0);
        assertEquals(400.0, store.loadAll().get(steve));
        store.save(steve, 0.0);
        assertFalse(store.loadAll().containsKey(steve), "a claimed bounty leaves no row");
    }
}
