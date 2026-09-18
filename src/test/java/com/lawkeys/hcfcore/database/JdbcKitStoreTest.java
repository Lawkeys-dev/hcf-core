package com.lawkeys.hcfcore.database;

import com.lawkeys.hcfcore.database.dao.JdbcKitStore;
import com.lawkeys.hcfcore.database.migration.SchemaMigrator;
import com.lawkeys.hcfcore.kit.Kit;
import com.lawkeys.hcfcore.kit.KitSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Kits, their waits and their layouts against a real SQLite database. */
class JdbcKitStoreTest {

    @TempDir
    Path tempDir;

    private SQLiteDataSource sqlite;
    private JdbcKitStore store;
    private final List<String> log = new ArrayList<>();

    @BeforeEach
    void setUp() {
        sqlite = new SQLiteDataSource();
        sqlite.setUrl("jdbc:sqlite:" + tempDir.resolve("test.db").toAbsolutePath());
        store = new JdbcKitStore(sqlite, log::add);
    }

    @Test
    void layoutsSurviveTheRoundTripAndAreReplacedWhole() throws Exception {
        store.initSchema();
        UUID alice = UUID.randomUUID();
        store.saveLayouts(alice, Map.of("archer", "0:2,1:40", "bard", "5:6"));
        assertEquals(Map.of("archer", "0:2,1:40", "bard", "5:6"), store.loadLayouts().get(alice));

        store.saveLayouts(alice, Map.of("bard", "5:7"));
        assertEquals(Map.of("bard", "5:7"), store.loadLayouts().get(alice));

        store.saveLayouts(alice, Map.of());
        assertEquals(null, store.loadLayouts().get(alice), "no layout left, no row left");
    }

    @Test
    void deletingAKitTakesItsLayoutsAndWaitsWithIt() throws Exception {
        store.initSchema();
        UUID alice = UUID.randomUUID();
        store.saveKit(new Kit("archer", null, null, 0, new byte[] {1}));
        store.saveLayouts(alice, Map.of("archer", "0:2", "bard", "5:6"));
        store.saveCooldowns(alice, Map.of("archer", 99L));

        store.deleteKit("archer");
        assertEquals(Map.of("bard", "5:6"), store.loadLayouts().get(alice));
        assertEquals(null, store.loadCooldowns().get(alice));
    }

    /** A delete that fails half-way leaves the kit and its waits as they were. */
    @Test
    void aFailedDeleteRemovesNothing() throws Exception {
        store.initSchema();
        UUID alice = UUID.randomUUID();
        store.saveKit(new Kit("archer", null, null, 0, new byte[] {1}));
        store.saveCooldowns(alice, Map.of("archer", 99L));
        try (Connection connection = sqlite.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DROP TABLE hcf_kit_layouts");
        }

        assertThrows(java.sql.SQLException.class, () -> store.deleteKit("archer"));
        assertEquals(List.of("archer"), store.loadKits().stream().map(Kit::id).toList());
        assertEquals(Map.of("archer", 99L), store.loadCooldowns().get(alice));
    }

    /** The icon column, never read, is dropped; the kits stay and can still be saved. */
    @Test
    void aDatabaseWithTheIconColumnKeepsItsKits() throws Exception {
        new SchemaMigrator(sqlite, log::add).migrate(KitSchema.migrations().subList(0, 2));
        try (Connection connection = sqlite.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO hcf_kits (id, display_name, icon, permission, contents) "
                    + "VALUES ('archer', 'Archer', 'BOW', 'hcfcore.kit.archer', X'01')");
        }

        store.initSchema();
        Kit archer = store.loadKits().iterator().next();
        assertEquals("Archer", archer.displayName());
        assertEquals("hcfcore.kit.archer", archer.permission());

        store.saveKit(new Kit("bard", null, null, 60, new byte[] {2}));
        assertEquals(List.of("archer", "bard"), store.loadKits().stream().map(Kit::id).sorted().toList());
    }

    /** A server that ran the version before the layout editor gains its table and keeps its kits. */
    @Test
    void aDatabaseFromBeforeTheLayoutEditorGainsItsTable() throws Exception {
        // Exactly what version 1 created, through the real migrator.
        new SchemaMigrator(sqlite, log::add).migrate(List.of(KitSchema.migrations().get(0)));
        try (Connection connection = sqlite.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO hcf_kits (id, display_name, icon, contents) "
                    + "VALUES ('archer', 'Archer', 'CHEST', X'01')");
        }

        store.initSchema();
        assertEquals(List.of("archer"), store.loadKits().stream().map(Kit::id).toList());
        assertEquals(Map.of(), store.loadLayouts());
        UUID alice = UUID.randomUUID();
        store.saveLayouts(alice, Map.of("archer", "0:2"));
        assertEquals(Map.of("archer", "0:2"), store.loadLayouts().get(alice));
    }
}
