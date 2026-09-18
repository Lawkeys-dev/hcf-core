package com.lawkeys.hcfcore.staff;

import com.lawkeys.hcfcore.staff.strike.Strike;
import com.lawkeys.hcfcore.staff.strike.StrikeLadder;
import com.lawkeys.hcfcore.staff.strike.StrikeManager;
import com.lawkeys.hcfcore.staff.strike.StrikeStore;
import com.lawkeys.hcfcore.staff.ticket.Ticket;
import com.lawkeys.hcfcore.staff.ticket.TicketManager;
import com.lawkeys.hcfcore.staff.ticket.TicketStatus;
import com.lawkeys.hcfcore.staff.ticket.TicketStore;
import com.lawkeys.hcfcore.staff.ticket.TicketType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reports, requests and strikes against teams, without a server. */
class TicketAndStrikeTest {

    private final AtomicLong now = new AtomicLong(1_000_000L);
    private UUID alice;
    private UUID bob;

    @BeforeEach
    void setUp() {
        alice = UUID.randomUUID();
        bob = UUID.randomUUID();
    }

    @Nested
    class Tickets {

        private TicketManager tickets;

        @BeforeEach
        void setUp() {
            tickets = new TicketManager(TicketStore.NO_OP, () -> 60L, now::get);
        }

        @Test
        void aReportEntersTheQueue() {
            Ticket ticket = tickets.open(TicketType.REPORT, alice, "Alice", bob, "Bob", "hacking")
                    .orElseThrow();
            assertEquals(TicketType.REPORT, ticket.type());
            assertEquals(TicketStatus.OPEN, ticket.status());
            assertEquals(1, tickets.size());
            assertEquals(1L, tickets.openCount());
        }

        @Test
        void aRequestNamesNobody() {
            Ticket ticket = tickets.open(TicketType.REQUEST, alice, "Alice", null, null, "stuck")
                    .orElseThrow();
            assertEquals(TicketType.REQUEST, ticket.type());
            assertEquals(null, ticket.target());
        }

        /** Without this one player can fill the queue faster than staff can read it. */
        @Test
        void aPlayerMustWaitBetweenTickets() {
            assertTrue(tickets.open(TicketType.REQUEST, alice, "Alice", null, null, "one").isPresent());
            assertTrue(tickets.open(TicketType.REQUEST, alice, "Alice", null, null, "two").isEmpty());
            assertEquals(60L, tickets.remainingCooldown(alice));

            now.addAndGet(60_000L);
            assertTrue(tickets.open(TicketType.REQUEST, alice, "Alice", null, null, "three").isPresent());
        }

        @Test
        void theCooldownIsPerPlayer() {
            tickets.open(TicketType.REQUEST, alice, "Alice", null, null, "one");
            assertTrue(tickets.open(TicketType.REQUEST, bob, "Bob", null, null, "mine").isPresent());
        }

        /** Two staff must not both believe a ticket is theirs. */
        @Test
        void aTicketCanOnlyBeClaimedOnce() {
            long id = tickets.open(TicketType.REPORT, alice, "Alice", bob, "Bob", "x")
                    .orElseThrow().id();

            assertEquals("First", tickets.claim(id, "First").orElseThrow().handledBy());
            assertTrue(tickets.claim(id, "Second").isEmpty());
            assertEquals(0L, tickets.openCount(), "claimed is no longer open");
            assertEquals(1, tickets.size(), "but it is still in the queue");
        }

        @Test
        void closingTakesItOutOfTheQueue() {
            long id = tickets.open(TicketType.REQUEST, alice, "Alice", null, null, "x")
                    .orElseThrow().id();
            assertEquals(TicketStatus.CLOSED, tickets.close(id, "Staff").orElseThrow().status());
            assertEquals(0, tickets.size());
            assertTrue(tickets.close(id, "Staff").isEmpty());
        }

        @Test
        void theQueueIsOldestFirst() {
            tickets.open(TicketType.REQUEST, alice, "Alice", null, null, "first");
            now.addAndGet(120_000L);
            tickets.open(TicketType.REQUEST, bob, "Bob", null, null, "second");

            List<String> messages = tickets.queue().stream().map(Ticket::message).toList();
            assertEquals(List.of("first", "second"), messages);
        }

        @Test
        void claimingSomethingThatIsNotThereIsRefused() {
            assertTrue(tickets.claim(999L, "Staff").isEmpty());
        }

        /** The cooldown is read at each use, so /hcf reload changes it. */
        @Test
        void theCooldownFollowsAReload() {
            AtomicLong cooldown = new AtomicLong(60L);
            TicketManager reloadable = new TicketManager(TicketStore.NO_OP, cooldown::get, now::get);
            reloadable.open(TicketType.REQUEST, alice, "Alice", null, null, "one");
            assertTrue(reloadable.open(TicketType.REQUEST, alice, "Alice", null, null, "two").isEmpty());

            cooldown.set(0L);
            assertTrue(reloadable.open(TicketType.REQUEST, alice, "Alice", null, null, "two").isPresent());
        }

        /** Cut to the column rather than refused by it, or the row would fail every save. */
        @Test
        void anOverlongMessageIsCutToTheColumn() {
            Ticket ticket = tickets.open(TicketType.REQUEST, alice, "Alice", null, null, "x".repeat(600))
                    .orElseThrow();
            assertEquals(Ticket.MAX_MESSAGE, ticket.message().length());
        }
    }

    /** A store that records what it is asked to write, and can be told to fail. */
    private static final class RecordingTicketStore implements TicketStore {

        final List<Ticket> saved = new ArrayList<>();
        long highestId;
        int failuresLeft;
        Runnable duringNextSave;

        @Override
        public void initSchema() {
        }

        @Override
        public Collection<Ticket> loadOpen() {
            return List.of();
        }

        @Override
        public long highestId() {
            return highestId;
        }

        @Override
        public void save(Ticket ticket) throws Exception {
            if (duringNextSave != null) {
                Runnable change = duringNextSave;
                duringNextSave = null;
                change.run();
            }
            if (failuresLeft > 0) {
                failuresLeft--;
                throw new Exception("database unavailable");
            }
            saved.add(ticket);
        }
    }

    @Nested
    class TicketPersistence {

        private RecordingTicketStore store;
        private TicketManager tickets;

        @BeforeEach
        void setUp() throws Exception {
            store = new RecordingTicketStore();
            tickets = new TicketManager(store, () -> 0L, now::get);
            tickets.loadAll();
        }

        @Test
        void aClosedTicketIsWrittenThenForgotten() throws Exception {
            long id = tickets.open(TicketType.REQUEST, alice, "Alice", null, null, "x").orElseThrow().id();
            assertEquals(1, tickets.flush());
            tickets.close(id, "Staff");

            assertEquals(1, tickets.flush());
            assertEquals(TicketStatus.CLOSED, store.saved.get(store.saved.size() - 1).status());
            assertEquals(0, tickets.flush(), "written once, then nothing is left to write");
        }

        /**
         * A save that fails must not forget the closing: the closed copy used to be
         * dropped before it was written, so a failed save left the database saying
         * "open" for ever and the ticket came back after a restart.
         */
        @Test
        void aFailedSaveIsRetriedAndTheClosingIsNotLost() throws Exception {
            long id = tickets.open(TicketType.REQUEST, alice, "Alice", null, null, "x").orElseThrow().id();
            tickets.close(id, "Staff");
            store.failuresLeft = 1;

            assertThrows(Exception.class, tickets::flush);
            assertEquals(1, tickets.flush());
            assertEquals(TicketStatus.CLOSED, store.saved.get(0).status());
        }

        /**
         * The save runs on an async task while commands keep changing tickets. A
         * claim landing mid-save must be written by the next flush, not have its
         * dirty mark wiped by the save that was already under way.
         */
        @Test
        void aChangeMadeWhileSavingIsSavedNextTime() throws Exception {
            long id = tickets.open(TicketType.REPORT, alice, "Alice", bob, "Bob", "x").orElseThrow().id();
            store.duringNextSave = () -> tickets.claim(id, "Staff");

            tickets.flush();
            assertEquals(1, tickets.flush());
            assertEquals(TicketStatus.CLAIMED, store.saved.get(store.saved.size() - 1).status());
        }

        /** Closed tickets stay in the table, so a restart must not reuse their numbers. */
        @Test
        void numberingContinuesPastClosedTickets() throws Exception {
            store.highestId = 41L;
            tickets.loadAll();
            assertEquals(42L, tickets.open(TicketType.REQUEST, alice, "Alice", null, null, "x")
                    .orElseThrow().id());
        }
    }

    @Nested
    class Strikes {

        private StrikeManager strikes;

        @BeforeEach
        void setUp() {
            strikes = new StrikeManager(StrikeStore.NO_OP, now::get);
        }

        @Test
        void aStrikeIsRecordedAgainstATeamAndCounts() {
            strikes.issue(alice, "Wizards", "Steve", "cheating", "Staff", 0L);
            assertEquals(1, strikes.activeCount(alice));
            assertEquals(0, strikes.activeCount(bob));
        }

        @Test
        void anExpiredStrikeStopsCountingButIsKept() {
            strikes.issue(alice, "Wizards", "", "spam", "Staff", 3_600L);
            assertEquals(1, strikes.activeCount(alice));

            now.addAndGet(3_600_001L);
            assertEquals(0, strikes.activeCount(alice), "it no longer counts");
            assertEquals(1, strikes.history(alice).size(), "but the record stays");
        }

        @Test
        void aStrikeWithNoDurationLastsTheMap() {
            strikes.issue(alice, "Wizards", "", "cheating", "Staff", 0L);
            now.addAndGet(10L * 365 * 24 * 3_600_000L);
            assertEquals(1, strikes.activeCount(alice));
        }

        @Test
        void historyIsNewestFirst() {
            strikes.issue(alice, "Wizards", "", "first", "Staff", 0L);
            now.addAndGet(1_000L);
            strikes.issue(alice, "Wizards", "", "second", "Staff", 0L);

            assertEquals("second", strikes.history(alice).get(0).reason());
        }

        @Test
        void pardoningRemovesOneStrike() {
            long id = strikes.issue(alice, "Wizards", "", "spam", "Staff", 0L).id();
            strikes.issue(alice, "Wizards", "", "other", "Staff", 0L);

            assertTrue(strikes.pardon(id));
            assertEquals(1, strikes.activeCount(alice));
            assertFalse(strikes.pardon(id));
        }

        @Test
        void aStrikeKeepsTheTeamNameAndTheMemberItWasFor() {
            Strike strike = strikes.issue(alice, "Wizards", "Steve", "cheating", "Staff", 0L);
            assertEquals("Wizards", strike.teamName());
            assertEquals("Steve", strike.subject());
            assertEquals("Staff", strike.issuedBy());
        }

        /** Seconds times a thousand, added to the clock, must not wrap into the past. */
        @Test
        void anEnormousValidityMeansForeverRatherThanOverflowing() {
            Strike strike = strikes.issue(alice, "Wizards", "", "spam", "Staff", Long.MAX_VALUE / 10);
            assertEquals(Strike.NEVER, strike.expiresAt());
            assertEquals(1, strikes.activeCount(alice));
        }

        /** A team disbanded by its strikes is still found by the name it had. */
        @Test
        void aTeamIsFoundByTheNameItsStrikesRecorded() {
            strikes.issue(alice, "Wizards", "", "old", "Staff", 0L);
            now.addAndGet(1_000L);
            strikes.issue(bob, "Wizards", "", "the name was taken again", "Staff", 0L);

            assertEquals(bob, strikes.latestUnderTeamName("wizards").orElseThrow().teamId(),
                    "the most recent holder of the name");
            assertTrue(strikes.latestUnderTeamName("nobody").isEmpty());
        }
    }

    @Nested
    class Ladder {

        private final StrikeLadder.Sanction halfThePoints = new StrikeLadder.Sanction(50, false, List.of());
        private final StrikeLadder.Sanction disband = new StrikeLadder.Sanction(0, true, List.of());

        /** The project owner's example: half the points at each of the first two, disbanded at the third. */
        private StrikeLadder ownersExample() {
            return StrikeLadder.of(Map.of(1, halfThePoints, 2, halfThePoints, 3, disband), m -> { });
        }

        @Test
        void aRungFiresAtExactlyItsCount() {
            StrikeLadder ladder = ownersExample();
            assertEquals(halfThePoints, ladder.at(1).orElseThrow());
            assertEquals(halfThePoints, ladder.at(2).orElseThrow());
            assertTrue(ladder.at(3).orElseThrow().disband());
            assertTrue(ladder.at(4).isEmpty(), "a team past its disband has nothing more to lose");
            assertTrue(StrikeLadder.empty().at(1).isEmpty());
        }

        @Test
        void theDisbandCountIsKnown() {
            assertEquals(3, ownersExample().disbandAt().orElseThrow());
            assertTrue(StrikeLadder.of(Map.of(1, halfThePoints), m -> { }).disbandAt().isEmpty());
        }

        @Test
        void aShareOfThePointsIsRoundedDown() {
            assertEquals(50L, StrikeLadder.pointsLost(100L, 50));
            assertEquals(50L, StrikeLadder.pointsLost(101L, 50));
            assertEquals(0L, StrikeLadder.pointsLost(1L, 50));
            assertEquals(7L, StrikeLadder.pointsLost(7L, 100));
            assertEquals(0L, StrikeLadder.pointsLost(0L, 50), "nothing from a team with nothing");
            assertEquals(0L, StrikeLadder.pointsLost(-20L, 50), "nor from a team below zero");
            assertEquals(Long.MAX_VALUE / 2, StrikeLadder.pointsLost(Long.MAX_VALUE, 50),
                    "no overflow, whatever the score");
        }

        @Test
        void placeholdersAreFilledIn() {
            assertEquals(List.of("broadcast Wizards reached 3"),
                    StrikeLadder.fill(List.of("broadcast %team% reached %strikes%"), "Wizards", 3));
        }

        @Test
        void aRungThatDoesNothingIsDropped() {
            List<String> warnings = new ArrayList<>();
            StrikeLadder ladder = StrikeLadder.of(Map.of(3, new StrikeLadder.Sanction(0, false, List.of())),
                    warnings::add);
            assertTrue(ladder.isEmpty());
            assertEquals(1, warnings.size());
        }

        @Test
        void anUnreachableRungIsDropped() {
            List<String> warnings = new ArrayList<>();
            StrikeLadder ladder = StrikeLadder.of(Map.of(0, disband), warnings::add);
            assertTrue(ladder.isEmpty());
            assertEquals(1, warnings.size());
        }

        @Test
        void aShareIsBroughtWithinZeroToAHundred() {
            assertEquals(100, new StrikeLadder.Sanction(250, false, List.of()).pointsLossPercent());
            assertEquals(0, new StrikeLadder.Sanction(-5, true, List.of()).pointsLossPercent());
        }

        @Test
        void rungsAreListedLowestFirst() {
            assertEquals(List.of(1, 2, 3), ownersExample().steps());
        }
    }

    /** A store that records what it is asked to write and delete. */
    private static final class RecordingStrikeStore implements StrikeStore {

        final List<Strike> saved = new ArrayList<>();
        final List<Long> deleted = new ArrayList<>();
        final List<Strike> stored = new ArrayList<>();
        int failuresLeft;
        Runnable duringSave = () -> { };

        @Override
        public void initSchema() {
        }

        @Override
        public Collection<Strike> loadAll() {
            return List.copyOf(stored);
        }

        @Override
        public void save(Strike strike) throws Exception {
            if (failuresLeft > 0) {
                failuresLeft--;
                throw new Exception("database unavailable");
            }
            saved.add(strike);
            duringSave.run();
        }

        @Override
        public void delete(long id) {
            deleted.add(id);
        }
    }

    @Nested
    class StrikePersistence {

        private RecordingStrikeStore store;
        private StrikeManager strikes;

        @BeforeEach
        void setUp() throws Exception {
            store = new RecordingStrikeStore();
            strikes = new StrikeManager(store, now::get);
            strikes.loadAll();
        }

        @Test
        void aStrikePardonedBeforeItWasSavedIsNeverWritten() throws Exception {
            long id = strikes.issue(alice, "Wizards", "", "mistake", "Staff", 0L).id();
            strikes.pardon(id);
            strikes.flush();

            assertTrue(store.saved.isEmpty());
            assertEquals(List.of(id), store.deleted);
        }

        @Test
        void aFailedWriteIsRetried() throws Exception {
            strikes.issue(alice, "Wizards", "", "spam", "Staff", 0L);
            store.failuresLeft = 1;

            assertThrows(Exception.class, strikes::flush);
            assertEquals(1, strikes.flush());
            assertEquals(1, store.saved.size());
        }

        /**
         * A strike issued while a write is in flight is not lost with the mark of the
         * one being written: each mark is cleared before its own write.
         */
        @Test
        void aStrikeIssuedDuringAFlushIsWrittenByTheNext() throws Exception {
            strikes.issue(alice, "Wizards", "", "first", "Staff", 0L);
            store.duringSave = () -> {
                store.duringSave = () -> { };
                strikes.issue(alice, "Wizards", "", "during", "Staff", 0L);
            };
            strikes.flush();
            assertEquals(1, store.saved.size());
            strikes.flush();
            assertEquals(List.of("first", "during"), store.saved.stream().map(Strike::reason).toList());
        }

        @Test
        void numberingContinuesAfterARestart() throws Exception {
            store.stored.add(new Strike(7L, alice, "Wizards", "", "old", "Staff", 1L, Strike.NEVER));
            strikes.loadAll();

            assertEquals(1, strikes.activeCount(alice));
            assertEquals(8L, strikes.issue(bob, "Knights", "", "new", "Staff", 0L).id());
        }
    }
}
