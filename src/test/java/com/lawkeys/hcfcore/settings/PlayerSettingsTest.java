package com.lawkeys.hcfcore.settings;

import com.lawkeys.hcfcore.database.dao.JdbcPlayerSettingsStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Player settings, without a server and against a real SQLite database. */
class PlayerSettingsTest {

    private final UUID alice = UUID.randomUUID();

    /** A store that records saves, can fail, and can have a change land mid-save. */
    private static final class RecordingStore implements PlayerSettingsStore {

        final List<Set<PlayerSetting>> saved = new ArrayList<>();
        int failuresLeft;
        Runnable duringNextSave;

        @Override
        public void initSchema() {
        }

        @Override
        public Map<UUID, Set<PlayerSetting>> loadAll() {
            return Map.of();
        }

        @Override
        public void save(UUID playerId, Set<PlayerSetting> disabled) throws Exception {
            if (duringNextSave != null) {
                Runnable change = duringNextSave;
                duringNextSave = null;
                change.run();
            }
            if (failuresLeft > 0) {
                failuresLeft--;
                throw new Exception("database unavailable");
            }
            saved.add(disabled);
        }
    }

    @Nested
    class Rules {

        private final RecordingStore store = new RecordingStore();
        private final PlayerSettings settings = new PlayerSettings(store);

        @Test
        void everythingIsOnUntilSwitchedOff() {
            for (PlayerSetting setting : PlayerSetting.values()) {
                assertTrue(settings.isOn(alice, setting));
            }
        }

        @Test
        void aToggleFlipsOneSettingOnly() {
            assertFalse(settings.toggle(alice, PlayerSetting.TIPS));
            assertFalse(settings.isOn(alice, PlayerSetting.TIPS));
            assertTrue(settings.isOn(alice, PlayerSetting.SCOREBOARD));
            assertTrue(settings.toggle(alice, PlayerSetting.TIPS));
        }

        /** Setting a switch to where it already is writes nothing. */
        @Test
        void anUnchangedSettingIsNotWritten() throws Exception {
            settings.set(alice, PlayerSetting.TIPS, true);
            assertEquals(0, settings.flush());
        }

        @Test
        void aFailedSaveIsRetried() throws Exception {
            settings.set(alice, PlayerSetting.COBBLESTONE, false);
            store.failuresLeft = 1;
            assertThrows(Exception.class, settings::flush);
            assertEquals(1, settings.flush());
            assertEquals(Set.of(PlayerSetting.COBBLESTONE), store.saved.get(0));
        }

        /** The save runs async while players keep clicking: a click mid-save is saved next time. */
        @Test
        void aChangeMadeWhileSavingIsSavedNextTime() throws Exception {
            settings.set(alice, PlayerSetting.TIPS, false);
            store.duringNextSave = () -> settings.set(alice, PlayerSetting.SCOREBOARD, false);
            settings.flush();
            assertEquals(1, settings.flush());
            assertEquals(Set.of(PlayerSetting.TIPS, PlayerSetting.SCOREBOARD), store.saved.get(1));
        }

        @Test
        void keysAreReadInAnyCase() {
            assertEquals(PlayerSetting.PRIVATE_MESSAGES, PlayerSetting.byKey(" Private-Messages ").orElseThrow());
            assertTrue(PlayerSetting.byKey("sounds").isEmpty());
        }
    }

    @Nested
    class Storage {

        @TempDir
        Path tempDir;

        private SQLiteDataSource sqlite;

        @BeforeEach
        void setUp() {
            sqlite = new SQLiteDataSource();
            sqlite.setUrl("jdbc:sqlite:" + tempDir.resolve("test.db").toAbsolutePath());
        }

        @Test
        void aChoiceOutlivesTheServer() throws Exception {
            PlayerSettings before = new PlayerSettings(new JdbcPlayerSettingsStore(sqlite, message -> { }));
            before.loadAll();
            before.set(alice, PlayerSetting.SCOREBOARD, false);
            before.set(alice, PlayerSetting.TIPS, false);
            before.flush();

            PlayerSettings after = new PlayerSettings(new JdbcPlayerSettingsStore(sqlite, message -> { }));
            after.loadAll();
            assertFalse(after.isOn(alice, PlayerSetting.SCOREBOARD));
            assertFalse(after.isOn(alice, PlayerSetting.TIPS));
            assertTrue(after.isOn(alice, PlayerSetting.COBBLESTONE));
        }

        /** A setting a later version removed is dropped, and said once, not silently. */
        @Test
        void anUnknownStoredSettingIsReportedOnce() throws Exception {
            List<String> log = new ArrayList<>();
            JdbcPlayerSettingsStore store = new JdbcPlayerSettingsStore(sqlite, log::add);
            store.initSchema();
            try (var connection = sqlite.getConnection(); var statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO hcf_player_settings (player_uuid, disabled) VALUES ('"
                        + alice + "', 'tips,night-vision')");
                statement.executeUpdate("INSERT INTO hcf_player_settings (player_uuid, disabled) VALUES ('"
                        + UUID.randomUUID() + "', 'night-vision')");
            }
            log.clear();

            assertEquals(Set.of(PlayerSetting.TIPS), store.loadAll().get(alice));
            assertEquals(1, log.stream().filter(line -> line.contains("night-vision")).count());
        }

        /** Switching everything back on removes the row: a default player costs nothing. */
        @Test
        void switchingEverythingBackOnLeavesNoRow() throws Exception {
            JdbcPlayerSettingsStore store = new JdbcPlayerSettingsStore(sqlite, message -> { });
            PlayerSettings settings = new PlayerSettings(store);
            settings.loadAll();
            settings.set(alice, PlayerSetting.TIPS, false);
            settings.flush();
            settings.set(alice, PlayerSetting.TIPS, true);
            settings.flush();

            assertTrue(store.loadAll().isEmpty());
        }
    }
}
