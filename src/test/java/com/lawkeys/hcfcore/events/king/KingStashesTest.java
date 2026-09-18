package com.lawkeys.hcfcore.events.king;

import com.lawkeys.hcfcore.claim.ClaimSettings.WarzoneRules.Area;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The stash of items owed back to Kings, and where a King may be sent. */
class KingStashesTest {

    private RecordingStore store;
    private KingStashes stashes;
    private final UUID alice = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        store = new RecordingStore();
        stashes = new KingStashes(store);
    }

    @Nested
    class Stashing {

        @Test
        void aStashIsKeptUntilRemoved() {
            assertTrue(stashes.put(alice, new byte[] {1, 2, 3}));
            assertTrue(stashes.has(alice));
            assertArrayEquals(new byte[] {1, 2, 3}, stashes.get(alice).orElseThrow());

            stashes.remove(alice);
            assertFalse(stashes.has(alice));
        }

        @Test
        void aSecondStashNeverOverwritesTheFirst() {
            // Overwriting would destroy the items the first one holds.
            assertTrue(stashes.put(alice, new byte[] {1}));
            assertFalse(stashes.put(alice, new byte[] {2}));
            assertArrayEquals(new byte[] {1}, stashes.get(alice).orElseThrow());
        }

        @Test
        void theBytesHandedInCannotBeChangedAfterwards() {
            byte[] contents = {1, 2, 3};
            stashes.put(alice, contents);
            contents[0] = 9;
            assertEquals(1, stashes.get(alice).orElseThrow()[0]);
        }
    }

    @Nested
    class Persistence {

        @Test
        void aStashIsWrittenAtTheNextFlushAndDeletedOnceHandedBack() throws Exception {
            stashes.put(alice, new byte[] {7});
            assertEquals(1, stashes.flush());
            assertArrayEquals(new byte[] {7}, store.rows.get(alice));

            stashes.remove(alice);
            assertEquals(1, stashes.flush());
            assertFalse(store.rows.containsKey(alice));
            assertEquals(0, stashes.flush(), "nothing left to do");
        }

        @Test
        void aFailedWriteStaysQueuedForTheNextFlush() throws Exception {
            stashes.put(alice, new byte[] {7});
            store.failNextSave = true;
            assertThrows(IllegalStateException.class, stashes::flush);
            assertFalse(store.rows.containsKey(alice));

            assertEquals(1, stashes.flush());
            assertArrayEquals(new byte[] {7}, store.rows.get(alice), "a crash now would not lose the items");
        }

        @Test
        void stashedAgainBeforeTheFlushReplacesTheRowInsteadOfDeletingIt() throws Exception {
            stashes.put(alice, new byte[] {1});
            stashes.flush();
            stashes.remove(alice);
            stashes.put(alice, new byte[] {2});

            stashes.flush();
            assertArrayEquals(new byte[] {2}, store.rows.get(alice));
            assertEquals(List.of(), store.deletes, "deleting would lose the new stash");
        }

        /** The main thread hands the items back and stashes new ones while the old row is being written. */
        @Test
        void aStashChangedDuringTheWriteIsWrittenAtTheNextFlush() throws Exception {
            stashes.put(alice, new byte[] {1});
            store.duringSave = () -> {
                stashes.remove(alice);
                stashes.put(alice, new byte[] {2});
            };
            stashes.flush();

            stashes.flush();
            assertArrayEquals(new byte[] {2}, store.rows.get(alice), "the new stash must not be left unwritten");
        }

        @Test
        void loadingReplacesTheCacheWithWhatIsStored() throws Exception {
            store.rows.put(alice, new byte[] {5});
            stashes.put(UUID.randomUUID(), new byte[] {1});

            stashes.loadAll();
            assertEquals(1, stashes.size());
            assertArrayEquals(new byte[] {5}, stashes.get(alice).orElseThrow());
            assertEquals(0, stashes.flush(), "what was loaded is not written back");
        }
    }

    @Nested
    class WhereTheKingIsSent {

        @Test
        void aColumnIsAlwaysInsideTheSquareBoundsIncluded() {
            Area area = new Area(100, -50, 8);
            Random random = new Random(7);
            boolean sawMinX = false;
            boolean sawMaxZ = false;
            for (int i = 0; i < 5_000; i++) {
                int[] column = WarzoneSpots.randomColumn(area, random);
                assertTrue(column[0] >= 92 && column[0] <= 108, "x " + column[0]);
                assertTrue(column[1] >= -58 && column[1] <= -42, "z " + column[1]);
                sawMinX |= column[0] == 92;
                sawMaxZ |= column[1] == -42;
            }
            assertTrue(sawMinX && sawMaxZ, "both bounds are reachable");
        }
    }

    // ------------------------------------------------------------------

    private static final class RecordingStore implements KingStashStore {

        final Map<UUID, byte[]> rows = new LinkedHashMap<>();
        final List<UUID> deletes = new ArrayList<>();
        boolean failNextSave;
        Runnable duringSave = () -> { };

        @Override
        public void initSchema() {
        }

        @Override
        public Map<UUID, byte[]> loadAll() {
            return new LinkedHashMap<>(rows);
        }

        @Override
        public void save(UUID playerId, byte[] contents) {
            if (failNextSave) {
                failNextSave = false;
                throw new IllegalStateException("database unreachable");
            }
            Runnable hook = duringSave;
            duringSave = () -> { };
            hook.run();
            rows.put(playerId, contents.clone());
        }

        @Override
        public void delete(UUID playerId) {
            deletes.add(playerId);
            rows.remove(playerId);
        }
    }
}
