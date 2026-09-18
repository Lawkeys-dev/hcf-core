package com.lawkeys.hcfcore.database;

import com.lawkeys.hcfcore.database.dao.JdbcPhaseStore;
import com.lawkeys.hcfcore.phase.PhaseState;
import com.lawkeys.hcfcore.phase.PhaseStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The map phase against a real SQLite database. */
class JdbcPhaseStoreTest {

    @TempDir
    Path tempDir;

    private JdbcPhaseStore store;
    private final List<String> log = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        SQLiteDataSource sqlite = new SQLiteDataSource();
        sqlite.setUrl("jdbc:sqlite:" + tempDir.resolve("test.db").toAbsolutePath());
        store = new JdbcPhaseStore(sqlite, log::add);
        store.initSchema();
    }

    @Test
    void aFreshDatabaseHasNoPhase() throws Exception {
        PhaseStore.Snapshot snapshot = store.load();
        assertEquals(PhaseState.NONE, snapshot.state());
        assertEquals(Set.of(), snapshot.sotwPvpEnabled());
    }

    @Test
    void theStateAndTheChoicesSurviveTheRoundTrip() throws Exception {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        PhaseState state = new PhaseState(1_800_000_000_000L, 0L, 1_700_000_000_000L, 0L, 0L);
        store.save(state, Set.of(alice, bob));

        PhaseStore.Snapshot snapshot = store.load();
        assertEquals(state, snapshot.state());
        assertEquals(Set.of(alice, bob), snapshot.sotwPvpEnabled());
    }

    @Test
    void savingAgainReplacesBothTheRowAndTheChoices() throws Exception {
        UUID alice = UUID.randomUUID();
        store.save(new PhaseState(1L, 0L, 0L, 0L, 0L), Set.of(alice));
        PhaseState later = new PhaseState(1L, 5L, 0L, 5L, 0L);
        store.save(later, Set.of());

        PhaseStore.Snapshot snapshot = store.load();
        assertEquals(later, snapshot.state());
        assertEquals(Set.of(), snapshot.sotwPvpEnabled(), "a SOTW that ended takes its choices with it");
    }

    @Test
    void aRunningPurgeSurvivesTheRoundTrip() throws Exception {
        PhaseState purging = new PhaseState(0L, 0L, 0L, 0L, 1_800_000_900_000L);
        store.save(purging, Set.of());
        assertEquals(purging, store.load().state());
    }

    /**
     * A server that ran the version before the Purge has its phase row without the
     * new column. Migration 2 must add it, keep the row, and read it as "no Purge
     * ever ran".
     */
    @Test
    void aDatabaseFromBeforeThePurgeGainsItsColumn() throws Exception {
        SQLiteDataSource legacy = new SQLiteDataSource();
        legacy.setUrl("jdbc:sqlite:" + tempDir.resolve("legacy.db").toAbsolutePath());
        new com.lawkeys.hcfcore.database.migration.SchemaMigrator(legacy, log::add)
                .migrate(List.of(com.lawkeys.hcfcore.phase.PhaseSchema.migrations().get(0)));
        try (var connection = legacy.getConnection();
             var insert = connection.prepareStatement("INSERT INTO hcf_map_phase (id, sotw_ends_at, eotw_since, "
                     + "sotw_schedule_handled, eotw_schedule_handled) VALUES (1, 7, 0, 0, 0)")) {
            insert.executeUpdate();
        }

        JdbcPhaseStore upgraded = new JdbcPhaseStore(legacy, log::add);
        upgraded.initSchema();
        assertEquals(new PhaseState(7L, 0L, 0L, 0L, 0L), upgraded.load().state());
    }

    @Test
    void theSchemaCanBeAppliedTwice() throws Exception {
        store.initSchema();
        assertEquals(PhaseState.NONE, store.load().state());
    }
}
