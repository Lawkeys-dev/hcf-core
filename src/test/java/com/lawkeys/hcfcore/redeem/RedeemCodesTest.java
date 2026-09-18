package com.lawkeys.hcfcore.redeem;

import com.lawkeys.hcfcore.database.dao.JdbcRedeemStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Redeem codes, without a server and against a real SQLite database. */
class RedeemCodesTest {

    private static final long NO_WAIT = 0L;

    private final AtomicLong now = new AtomicLong(1_000_000L);
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID carol = UUID.randomUUID();

    @Nested
    class Rules {

        private final RedeemCodes codes = new RedeemCodes(RedeemStore.NO_OP, now::get);

        @Test
        void aCodeGivesItsRewardOnce() {
            codes.create("SUMMER", 0, "Staff", "eco give %player% 500");
            RedeemCodes.Outcome first = codes.redeem("summer", alice, NO_WAIT);
            assertEquals(RedeemCodes.Status.REDEEMED, first.status(), "codes are matched without case");
            assertEquals(List.of("eco give %player% 500"), first.commands());
            assertEquals(RedeemCodes.Status.ALREADY_REDEEMED, codes.redeem("SUMMER", alice, NO_WAIT).status());
        }

        @Test
        void aCapStopsTheCodeWhenReached() {
            codes.create("FIRST2", 2, "Staff", "x");
            assertEquals(RedeemCodes.Status.REDEEMED, codes.redeem("FIRST2", alice, NO_WAIT).status());
            assertEquals(RedeemCodes.Status.REDEEMED, codes.redeem("FIRST2", bob, NO_WAIT).status());
            assertEquals(RedeemCodes.Status.EXHAUSTED, codes.redeem("FIRST2", carol, NO_WAIT).status());
        }

        /** A code with no reward yet must not be used up by players who get nothing for it. */
        @Test
        void aCodeWithNoRewardIsNotReadyAndNotUsedUp() {
            codes.create("SOON", 1, "Staff", null);
            assertEquals(RedeemCodes.Status.EMPTY, codes.redeem("SOON", alice, NO_WAIT).status());
            assertEquals(0, codes.get("SOON").orElseThrow().uses());
        }

        /** Otherwise codes could be found by trying names as fast as chat allows. */
        @Test
        void aFailedAttemptMakesThePlayerWait() {
            codes.create("REAL", 0, "Staff", "x");
            assertEquals(RedeemCodes.Status.UNKNOWN, codes.redeem("GUESS", alice, 3_000L).status());
            assertEquals(RedeemCodes.Status.TOO_SOON, codes.redeem("REAL", alice, 3_000L).status(),
                    "even a real code waits");
            now.addAndGet(3_000L);
            assertEquals(RedeemCodes.Status.REDEEMED, codes.redeem("REAL", alice, 3_000L).status());
        }

        @Test
        void resettingLetsItBeUsedAgain() {
            codes.create("CODE", 1, "Staff", "x");
            codes.redeem("CODE", alice, NO_WAIT);
            assertTrue(codes.reset("CODE", null));
            assertEquals(RedeemCodes.Status.REDEEMED, codes.redeem("CODE", bob, NO_WAIT).status());
        }

        @Test
        void resettingOnePlayerLeavesTheOthers() {
            codes.create("CODE", 0, "Staff", "x");
            codes.redeem("CODE", alice, NO_WAIT);
            codes.redeem("CODE", bob, NO_WAIT);
            assertTrue(codes.reset("CODE", alice));
            assertFalse(codes.reset("CODE", carol), "carol never used it");
            assertEquals(RedeemCodes.Status.REDEEMED, codes.redeem("CODE", alice, NO_WAIT).status());
            assertEquals(RedeemCodes.Status.ALREADY_REDEEMED, codes.redeem("CODE", bob, NO_WAIT).status());
        }

        /** An existing code is deleted first, never silently replaced. */
        @Test
        void aCodeCannotBeCreatedTwice() {
            assertTrue(codes.create("ONCE", 0, "Staff", "x"));
            assertFalse(codes.create("once", 5, "Staff", "y"));
        }

        @Test
        void onlySensibleCodesAreAccepted() {
            assertFalse(codes.create("ab", 0, "Staff", "x"), "too short");
            assertFalse(codes.create("has space", 0, "Staff", "x"));
            assertFalse(codes.create("x".repeat(33), 0, "Staff", "x"), "too long");
            assertTrue(codes.create("Summer-2026_vip", 0, "Staff", "x"));
        }

        @Test
        void rewardsCanBeAddedAndRemovedByNumber() {
            codes.create("CODE", 0, "Staff", "first");
            codes.addCommand("CODE", "second");
            assertTrue(codes.removeCommand("CODE", 1));
            assertFalse(codes.removeCommand("CODE", 5));
            assertEquals(List.of("second"), codes.get("CODE").orElseThrow().commands());
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

        private RedeemCodes fresh() throws Exception {
            RedeemCodes codes = new RedeemCodes(new JdbcRedeemStore(sqlite, message -> { }), now::get);
            codes.loadAll();
            return codes;
        }

        /** The point of storing redemptions: once per player survives a restart. */
        @Test
        void aRedemptionOutlivesTheServer() throws Exception {
            RedeemCodes before = fresh();
            before.create("SUMMER", 10, "Staff", "eco give %player% 500");
            before.addCommand("SUMMER", "kit give %player% vip");
            before.redeem("SUMMER", alice, NO_WAIT);
            before.flush();

            RedeemCodes after = fresh();
            RedeemCode code = after.get("summer").orElseThrow();
            assertEquals(List.of("eco give %player% 500", "kit give %player% vip"), code.commands());
            assertEquals(10, code.maxUses());
            assertEquals(RedeemCodes.Status.ALREADY_REDEEMED, after.redeem("SUMMER", alice, NO_WAIT).status());
        }

        @Test
        void resetsAndDeletesReachTheDatabase() throws Exception {
            RedeemCodes before = fresh();
            before.create("KEEP", 0, "Staff", "x");
            before.create("GONE", 0, "Staff", "x");
            before.redeem("KEEP", alice, NO_WAIT);
            before.redeem("KEEP", bob, NO_WAIT);
            before.reset("KEEP", alice);
            before.delete("GONE");
            before.flush();

            RedeemCodes after = fresh();
            assertTrue(after.get("GONE").isEmpty());
            assertEquals(1, after.get("KEEP").orElseThrow().uses(), "only bob's redemption is left");
        }

        /**
         * A retry after a failure past the insert writes the same redemption twice.
         * That must not trip the primary key and stall every write queued behind it.
         */
        @Test
        void writingTheSameRedemptionTwiceIsHarmless() throws Exception {
            JdbcRedeemStore store = new JdbcRedeemStore(sqlite, message -> { });
            store.initSchema();
            store.saveCode(new RedeemCode("CODE", List.of("x"), 0, "Staff", 1L, java.util.Set.of()));
            store.addRedemption("code", alice, 5L);
            store.addRedemption("code", alice, 6L);
            assertEquals(1, List.copyOf(store.loadAll()).get(0).uses());
        }
    }
}
