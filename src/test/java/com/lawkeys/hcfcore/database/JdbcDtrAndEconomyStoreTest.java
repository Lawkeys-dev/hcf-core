package com.lawkeys.hcfcore.database;

import com.lawkeys.hcfcore.database.dao.JdbcDtrStore;
import com.lawkeys.hcfcore.database.dao.JdbcEconomyStore;
import com.lawkeys.hcfcore.dtr.DtrState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** DTR and balances against a real SQLite database: both are update-then-insert. */
class JdbcDtrAndEconomyStoreTest {

    @TempDir
    Path tempDir;

    private JdbcDtrStore dtr;
    private JdbcEconomyStore economy;
    private final List<String> log = new ArrayList<>();
    private final UUID first = UUID.randomUUID();
    private final UUID second = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        SQLiteDataSource sqlite = new SQLiteDataSource();
        sqlite.setUrl("jdbc:sqlite:" + tempDir.resolve("test.db").toAbsolutePath());
        dtr = new JdbcDtrStore(sqlite, log::add);
        dtr.initSchema();
        economy = new JdbcEconomyStore(sqlite, log::add);
        economy.initSchema();
    }

    @Test
    void aDtrStateIsInsertedThenUpdatedInPlaceAndDeleted() throws Exception {
        dtr.save(new DtrState(first, 3.5, 0L));
        dtr.save(new DtrState(second, -1.25, 1_700_000_000_000L));
        dtr.save(new DtrState(first, 1.1, 42L));

        assertEquals(Set.of(new DtrState(first, 1.1, 42L), new DtrState(second, -1.25, 1_700_000_000_000L)),
                Set.copyOf(dtr.loadAll()));

        dtr.delete(first);
        assertEquals(List.of(new DtrState(second, -1.25, 1_700_000_000_000L)), List.copyOf(dtr.loadAll()));
    }

    @Test
    void aBalanceIsInsertedThenUpdatedInPlace() throws Exception {
        economy.save(first, 100.0);
        economy.save(second, 0.01);
        economy.save(first, 12_345_678.9);

        assertEquals(Map.of(first, 12_345_678.9, second, 0.01), economy.loadAll());
    }
}
