package com.lawkeys.hcfcore.database;

import com.lawkeys.hcfcore.database.dao.JdbcDeathbanStore;
import com.lawkeys.hcfcore.pvp.Deathban;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A deathban until the map ends is stored with an expiry of {@code Long.MAX_VALUE}
 * in a {@code BIGINT} column. These check, on a real SQLite database, that the
 * value survives the round trip and that neither loading nor purging mistakes it
 * for an expired ban.
 */
class JdbcDeathbanStoreTest {

    @TempDir
    Path tempDir;

    private JdbcDeathbanStore store;
    private final List<String> log = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        SQLiteDataSource sqlite = new SQLiteDataSource();
        sqlite.setUrl("jdbc:sqlite:" + tempDir.resolve("test.db").toAbsolutePath());
        store = new JdbcDeathbanStore(sqlite, log::add);
        store.initSchema();
    }

    @Test
    void aBanUntilTheMapEndsComesBackAsOne() throws Exception {
        UUID alice = UUID.randomUUID();
        store.save(new Deathban(alice, Deathban.UNTIL_MAP_END, "eotw"));

        Collection<Deathban> loaded = store.loadActive(System.currentTimeMillis());
        assertEquals(1, loaded.size());
        Deathban ban = loaded.iterator().next();
        assertEquals(alice, ban.playerId());
        assertTrue(ban.isUntilMapEnd());
    }

    @Test
    void purgingExpiredBansLeavesItAlone() throws Exception {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        long now = System.currentTimeMillis();
        store.save(new Deathban(alice, Deathban.UNTIL_MAP_END, "eotw"));
        store.save(new Deathban(bob, now - 1_000L, "died"));

        assertEquals(1, store.purgeExpired(now), "only the expired ordinary ban goes");
        assertEquals(List.of(alice), store.loadActive(now).stream().map(Deathban::playerId).toList());
    }
}
