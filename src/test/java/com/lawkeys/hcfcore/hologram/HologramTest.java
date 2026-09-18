package com.lawkeys.hcfcore.hologram;

import com.lawkeys.hcfcore.database.dao.JdbcHologramStore;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Holograms, without a server and against a real SQLite database. */
class HologramTest {

    @Nested
    class Text {

        private final List<String> asked = new ArrayList<>();

        private Optional<HologramText.Entry> board(String name, int rank) {
            asked.add(name + ":" + rank);
            return name.equals("kills") && rank == 1
                    ? Optional.of(new HologramText.Entry("Alice", "42"))
                    : Optional.empty();
        }

        @Test
        void aLeaderboardLineIsFilledIn() {
            assertEquals(List.of("1. Alice - 42"), HologramText.render(
                    List.of("1. %top_kills_1_name% - %top_kills_1_value%"), this::board, "---"));
        }

        /** An empty rank shows the filler, not the raw placeholder a player would read literally. */
        @Test
        void aRankNobodyHoldsShowsTheFiller() {
            assertEquals(List.of("2. --- "), HologramText.render(
                    List.of("2. %top_kills_2_name% %top_kills_2_value%"), this::board, "---"));
        }

        @Test
        void aRankOutOfBoundsIsNeverAskedFor() {
            HologramText.render(List.of("%top_kills_0_name% %top_kills_999_name%"), this::board, "-");
            assertTrue(asked.isEmpty());
        }

        /** Otherwise a leaderboard name holding "$1" would be read as a regex group. */
        @Test
        void aValueIsInsertedLiterally() {
            assertEquals(List.of("$1\\x"), HologramText.render(List.of("%top_kills_1_name%"),
                    (name, rank) -> Optional.of(new HologramText.Entry("$1\\x", "")), "-"));
        }

        @Test
        void onlyHologramsWithABoardAreDynamic() {
            assertTrue(HologramText.isDynamic(List.of("title", "%top_deaths_3_value%")));
            assertFalse(HologramText.isDynamic(List.of("&6Welcome to spawn", "%player%")));
        }
    }

    @Nested
    class Manager {

        private final Holograms holograms = new Holograms(HologramStore.NO_OP);

        @Test
        void idsAreCaseInsensitiveAndChecked() {
            assertTrue(holograms.create("Spawn", "world", 0, 64, 0, List.of("x")));
            assertTrue(holograms.get("SPAWN").isPresent());
            assertFalse(holograms.create("spawn", "world", 0, 64, 0, List.of("y")), "taken");
            assertFalse(holograms.create("has space", "world", 0, 64, 0, List.of("y")));
        }

        @Test
        void aWallOfTextIsRefused() {
            List<String> tooMany = new ArrayList<>();
            for (int i = 0; i <= Holograms.MAX_LINES; i++) {
                tooMany.add("line " + i);
            }
            assertFalse(holograms.create("wall", "world", 0, 64, 0, tooMany));
        }

        /** Negative coordinates fall into the right chunk: -1 is chunk -1, not 0. */
        @Test
        void theChunkOfANegativePositionIsRight() {
            Hologram hologram = new Hologram("x", "world", -0.5, 64, -16.5, List.of());
            assertEquals(-1, hologram.chunkX());
            assertEquals(-2, hologram.chunkZ());
        }
    }

    @Nested
    class Storage {

        @TempDir
        Path tempDir;

        @Test
        void aHologramOutlivesTheServerAndADeleteIsFinal() throws Exception {
            SQLiteDataSource sqlite = new SQLiteDataSource();
            sqlite.setUrl("jdbc:sqlite:" + tempDir.resolve("test.db").toAbsolutePath());

            Holograms before = new Holograms(new JdbcHologramStore(sqlite, message -> { }));
            before.loadAll();
            before.create("kills", "world", 10.5, 70, -3.25, List.of("&6Top", "", "%top_kills_1_name%"));
            before.create("gone", "world", 0, 64, 0, List.of("bye"));
            before.flush();
            before.delete("gone");
            before.flush();

            Holograms after = new Holograms(new JdbcHologramStore(sqlite, message -> { }));
            after.loadAll();
            Hologram kills = after.get("kills").orElseThrow();
            assertEquals(List.of("&6Top", "", "%top_kills_1_name%"), kills.lines(), "a blank spacer line survives");
            assertEquals(-3.25, kills.z());
            assertTrue(after.get("gone").isEmpty());
        }
    }
}
