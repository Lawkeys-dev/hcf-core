package com.lawkeys.hcfcore.lives;

import com.lawkeys.hcfcore.database.dao.JdbcLivesStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lives, without a server and against a real SQLite database. */
class LivesTest {

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    /** use-on-login: a deathbanned player with a life spends it and comes in. */
    @Nested
    class LifeAtLogin {

        private final Lives lives = new Lives(LivesStore.NO_OP, () -> 0);
        private final com.lawkeys.hcfcore.pvp.Deathban timed =
                new com.lawkeys.hcfcore.pvp.Deathban(alice, 5_000L, "died");
        private final com.lawkeys.hcfcore.pvp.Deathban untilMapEnd =
                new com.lawkeys.hcfcore.pvp.Deathban(alice, com.lawkeys.hcfcore.pvp.Deathban.UNTIL_MAP_END, "EOTW");

        @Test
        void aLifeIsSpentToComeIn() {
            lives.set(alice, 2);
            assertTrue(LoginWaiver.spend(true, true, timed, lives, alice));
            assertEquals(1, lives.get(alice));
        }

        @Test
        void aBanUntilTheMapEndsIsNeverWaivedAndCostsNothing() {
            lives.set(alice, 2);
            assertFalse(LoginWaiver.spend(true, true, untilMapEnd, lives, alice));
            assertEquals(2, lives.get(alice));
        }

        @Test
        void nothingIsSpentWhenTheModuleOrTheRuleIsOff() {
            lives.set(alice, 2);
            assertFalse(LoginWaiver.spend(false, true, timed, lives, alice));
            assertFalse(LoginWaiver.spend(true, false, timed, lives, alice));
            assertFalse(LoginWaiver.spend(true, true, timed, null, alice));
            assertEquals(2, lives.get(alice));
        }

        @Test
        void withoutALifeTheBanStands() {
            assertFalse(LoginWaiver.spend(true, true, timed, lives, alice));
            assertEquals(0, lives.get(alice));
        }
    }

    @Nested
    class Rules {

        private final AtomicInteger starting = new AtomicInteger(0);
        private final Lives lives = new Lives(LivesStore.NO_OP, starting::get);

        @Test
        void aNewPlayerHoldsTheStartingAmount() {
            starting.set(2);
            assertEquals(2, lives.get(alice));
            assertTrue(lives.spendOne(alice));
            assertEquals(1, lives.get(alice));
        }

        @Test
        void aLifeCannotBeSpentThatIsNotHeld() {
            assertFalse(lives.spendOne(alice));
            assertEquals(0, lives.get(alice));
        }

        @Test
        void aTransferMovesLivesOrNothing() {
            lives.add(alice, 3);
            assertEquals(Lives.Transfer.DONE, lives.transfer(alice, bob, 2));
            assertEquals(1, lives.get(alice));
            assertEquals(2, lives.get(bob));
            assertEquals(Lives.Transfer.NOT_ENOUGH, lives.transfer(alice, bob, 5));
            assertEquals(1, lives.get(alice), "a refused transfer takes nothing");
            assertEquals(Lives.Transfer.SAME_PLAYER, lives.transfer(alice, alice, 1));
            assertEquals(Lives.Transfer.INVALID_AMOUNT, lives.transfer(alice, bob, 0));
        }

        @Test
        void addingNeverOverflows() {
            lives.set(alice, Integer.MAX_VALUE - 1);
            assertEquals(Integer.MAX_VALUE, lives.add(alice, 10));
        }

        /**
         * Two revives racing on one player's last lives must not both succeed: each
         * spend is a read-modify-write, the same trap economy/ pinned for balances.
         */
        @Test
        void concurrentSpendsNeverSpendTheSameLifeTwice() throws Exception {
            lives.set(alice, 10);
            ExecutorService pool = Executors.newFixedThreadPool(16);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 16; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return lives.spendOne(alice);
                }));
            }
            start.countDown();
            int spent = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    spent++;
                }
            }
            pool.shutdown();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
            assertEquals(10, spent);
            assertEquals(0, lives.get(alice));
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
        void livesOutliveTheServer() throws Exception {
            Lives before = new Lives(new JdbcLivesStore(sqlite, message -> { }), () -> 0);
            before.loadAll();
            before.add(alice, 3);
            before.spendOne(alice);
            before.flush();

            Lives after = new Lives(new JdbcLivesStore(sqlite, message -> { }), () -> 0);
            after.loadAll();
            assertEquals(2, after.get(alice));
        }

        /** A player never touched costs no row, as an untouched balance does. */
        @Test
        void aPlayerWhoseLivesNeverChangedHasNoRow() throws Exception {
            JdbcLivesStore store = new JdbcLivesStore(sqlite, message -> { });
            Lives lives = new Lives(store, () -> 5);
            lives.loadAll();
            assertEquals(5, lives.get(alice));
            lives.flush();
            assertEquals(Map.of(), store.loadAll());
        }
    }
}
