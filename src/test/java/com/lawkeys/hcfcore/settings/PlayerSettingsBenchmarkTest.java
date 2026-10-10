package com.lawkeys.hcfcore.settings;

import com.lawkeys.hcfcore.database.dao.JdbcPlayerSettingsStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

class PlayerSettingsBenchmarkTest {

    @TempDir
    Path tempDir;

    private SQLiteDataSource sqlite;

    @BeforeEach
    void setUp() {
        sqlite = new SQLiteDataSource();
        sqlite.setUrl("jdbc:sqlite:" + tempDir.resolve("benchmark.db").toAbsolutePath());
    }

    @Test
    void benchmarkFlushPerformance() throws Exception {
        JdbcPlayerSettingsStore store = new JdbcPlayerSettingsStore(sqlite, message -> {});
        store.initSchema();
        PlayerSettings settings = new PlayerSettings(store);

        int playerCount = 500;
        List<UUID> players = new ArrayList<>();
        for (int i = 0; i < playerCount; i++) {
            UUID id = UUID.randomUUID();
            players.add(id);
            // dirty half with settings, half cleared
            if (i % 2 == 0) {
                settings.set(id, PlayerSetting.SCOREBOARD, false);
                settings.set(id, PlayerSetting.TIPS, false);
            } else {
                settings.set(id, PlayerSetting.TIPS, false);
            }
        }

        long start = System.currentTimeMillis();
        int written = settings.flush();
        long elapsed = System.currentTimeMillis() - start;

        System.out.println("BENCHMARK_RESULT: flushed " + written + " records in " + elapsed + " ms");
    }
}
