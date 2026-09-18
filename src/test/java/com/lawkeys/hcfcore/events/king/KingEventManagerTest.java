package com.lawkeys.hcfcore.events.king;

import com.lawkeys.hcfcore.events.king.KingUpdate.Type;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Rule-level tests for Kill the King: a whole reign, played without a server. */
class KingEventManagerTest {

    private static final ZoneId UTC = ZoneId.of("UTC");
    /** 12:00:00 UTC on some day, so schedule tests have a known wall clock. */
    private static final long NOON = ZonedDateTime.of(2026, 9, 11, 12, 0, 0, 0, UTC).toInstant().toEpochMilli();

    private final AtomicLong clock = new AtomicLong(NOON);
    private final FixedDraw draw = new FixedDraw();
    private KingSettings settings;
    private KingEventManager manager;

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID carol = UUID.randomUUID();
    private final UUID red = UUID.randomUUID();
    private final UUID blue = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        settings = new KingSettings(true, UTC, List.of(definition(600, 2, List.of(), List.of())));
        manager = new KingEventManager(() -> settings, clock::get, draw);
    }

    private static KingEventDefinition definition(long durationSeconds, int minimumPlayers,
                                                  List<Long> marks, List<LocalTime> schedule) {
        return new KingEventDefinition("ktk", "&4Kill the King", "world", durationSeconds, minimumPlayers,
                marks, schedule, 20L, new OutsidePenalty(3L, 2.0, 1, 10L, 3), KingKit.empty(), List.of());
    }

    private KingEventDefinition ktk() {
        return settings.definitions().get(0);
    }

    private void advanceSeconds(long seconds) {
        clock.addAndGet(seconds * 1000L);
    }

    /** Opens and crowns Alice of team red, Bob of team blue being the other candidate. */
    private void crownAlice() {
        assertTrue(manager.open(ktk()));
        draw.next = 0;
        KingUpdate crowned = manager.crown(List.of(new Candidate(alice, red), new Candidate(bob, blue))).orElseThrow();
        assertEquals(Type.CROWNED, crowned.type());
        assertEquals(alice, crowned.kingId());
    }

    private static List<Type> types(List<KingUpdate> updates) {
        return updates.stream().map(KingUpdate::type).toList();
    }

    // ------------------------------------------------------------------

    @Nested
    class Crowning {

        @Test
        void theKingIsDrawnAmongTheCandidates() {
            assertTrue(manager.open(ktk()));
            draw.next = 1;
            KingUpdate crowned = manager.crown(List.of(new Candidate(alice, red), new Candidate(bob, blue)))
                    .orElseThrow();

            assertEquals(Type.CROWNED, crowned.type());
            assertEquals(bob, crowned.kingId());
            assertNull(crowned.winnerId(), "being crowned wins nothing");
            assertTrue(manager.isKing(bob));
            assertFalse(manager.isKing(alice));
            assertEquals(2, draw.lastBound, "drawn among exactly the candidates given");
        }

        @Test
        void tooFewCandidatesCallItOffAndFreeTheSlot() {
            assertTrue(manager.open(ktk()));
            KingUpdate update = manager.crown(List.of(new Candidate(alice, red))).orElseThrow();

            assertEquals(Type.CANCELLED, update.type());
            assertEquals(KingMessages.CANCELLED_NOT_ENOUGH_PLAYERS, update.messageKey());
            assertEquals("2", update.placeholders().get("minimum"));
            assertNull(update.kingId());
            assertTrue(manager.getCurrent().isEmpty());
            assertTrue(manager.open(ktk()), "the slot is free again");
        }

        @Test
        void nobodyAtAllIsTooFewEvenForAMinimumOfOne() {
            settings = new KingSettings(true, UTC, List.of(definition(600, 1, List.of(), List.of())));
            assertTrue(manager.open(ktk()));
            assertEquals(Type.CANCELLED, manager.crown(List.of()).orElseThrow().type());
        }

        @Test
        void onlyOneRunAtATime() {
            crownAlice();
            assertFalse(manager.open(ktk()), "a second King would share the same warzone");
        }

        @Test
        void aRunIsNotCrownedTwice() {
            crownAlice();
            assertTrue(manager.crown(List.of(new Candidate(carol, null), new Candidate(bob, blue))).isEmpty());
            assertTrue(manager.isKing(alice));
        }

        @Test
        void nobodyIsKingWhileASpotIsBeingFound() {
            assertTrue(manager.open(ktk()));
            assertFalse(manager.getCurrent().orElseThrow().isReigning());
            assertTrue(manager.tick(false).isEmpty(), "no King, so nothing to punish or count down");
        }
    }

    @Nested
    class TheClock {

        @Test
        void aKingWhoOutlivesTheClockWinsIt() {
            crownAlice();
            advanceSeconds(599);
            assertFalse(types(manager.tick(true)).contains(Type.SURVIVED));

            advanceSeconds(1);
            List<KingUpdate> updates = manager.tick(true);

            assertEquals(List.of(Type.SURVIVED), types(updates));
            assertEquals(alice, updates.get(0).winnerId(), "the King takes the rewards");
            assertTrue(manager.getCurrent().isEmpty());
        }

        @Test
        void remainingTimeMarksAreAnnouncedOnceEach() {
            settings = new KingSettings(true, UTC, List.of(definition(600, 2, List.of(600L, 300L, 60L), List.of())));
            crownAlice();

            assertTrue(manager.tick(true).isEmpty(), "a mark at the full reign would fire at the crowning");
            advanceSeconds(300);
            assertEquals(List.of(Type.PROGRESS), types(manager.tick(true)));
            advanceSeconds(1);
            assertTrue(manager.tick(true).isEmpty(), "each mark fires once");
            advanceSeconds(239);
            assertEquals(List.of(Type.PROGRESS), types(manager.tick(true)));
        }

        @Test
        void theRemainingTimeIsRoundedUp() {
            crownAlice();
            clock.addAndGet(500L);
            assertEquals(600L, manager.getRemainingSeconds());
        }
    }

    /** Leaving the warzone: warned at once, spared for the grace, then withered. */
    @Nested
    class OutsideTheWarzone {

        @Test
        void leavingIsAnnouncedOnceAndTheGraceSparesHim() {
            crownAlice();
            assertEquals(List.of(Type.LEFT_ZONE), types(manager.tick(false)));
            advanceSeconds(1);
            assertTrue(manager.tick(false).isEmpty(), "within the grace, and not warned twice");
            advanceSeconds(1);
            assertTrue(manager.tick(false).isEmpty());

            advanceSeconds(1);
            List<KingUpdate> updates = manager.tick(false);
            assertEquals(List.of(Type.PENALTY), types(updates));
            assertEquals(1, updates.get(0).witherLevel());
        }

        @Test
        void theWitherGrowsWithTheTimeOutsideAndStopsAtItsCap() {
            crownAlice();
            manager.tick(false);
            advanceSeconds(3);
            assertEquals(1, penalty(manager.tick(false)).witherLevel());
            advanceSeconds(10);
            assertEquals(2, penalty(manager.tick(false)).witherLevel());
            advanceSeconds(10);
            assertEquals(3, penalty(manager.tick(false)).witherLevel());
            advanceSeconds(60);
            assertEquals(3, penalty(manager.tick(false)).witherLevel(), "capped at the configured maximum");
        }

        @Test
        void damageCoversOnlyTheTimeOutsidePastTheGrace() {
            crownAlice();
            manager.tick(false);
            // Ticked late: 5 seconds out, of which 2 are past the 3-second grace.
            advanceSeconds(5);
            assertEquals(4.0, penalty(manager.tick(false)).damage(), 1e-9, "2 seconds at 2.0 per second");
            advanceSeconds(1);
            assertEquals(2.0, penalty(manager.tick(false)).damage(), 1e-9);
        }

        @Test
        void comingBackEndsTheExcursionAndTheNextOneStartsOverFromTheGrace() {
            crownAlice();
            manager.tick(false);
            advanceSeconds(25);
            assertEquals(3, penalty(manager.tick(false)).witherLevel());

            advanceSeconds(1);
            assertEquals(List.of(Type.RETURNED), types(manager.tick(true)));
            advanceSeconds(1);
            assertTrue(manager.tick(true).isEmpty(), "told once that they are back");

            advanceSeconds(1);
            assertEquals(List.of(Type.LEFT_ZONE), types(manager.tick(false)));
            advanceSeconds(3);
            assertEquals(1, penalty(manager.tick(false)).witherLevel(), "one excursion, not a running total");
        }

        private KingUpdate penalty(List<KingUpdate> updates) {
            return updates.stream().filter(update -> update.type() == Type.PENALTY).findFirst().orElseThrow();
        }
    }

    /**
     * Who may win it by killing the King. The rule the project owner chose: only a
     * player outside the King's team - so a King drawn from your own team is not a
     * free prize, and the King cannot leave their team to hand it to them either.
     */
    @Nested
    class TheKill {

        @Test
        void aKillByAnEnemyWinsIt() {
            crownAlice();
            KingUpdate update = manager.kingDied(bob, blue, red).orElseThrow();

            assertEquals(Type.KILLED, update.type());
            assertEquals(bob, update.winnerId());
            assertEquals(alice, update.kingId());
            assertTrue(manager.getCurrent().isEmpty());
        }

        @Test
        void aTeammateCannotWinIt() {
            crownAlice();
            UUID aliceTeammate = UUID.randomUUID();
            KingUpdate update = manager.kingDied(aliceTeammate, red, red).orElseThrow();

            assertEquals(Type.DIED, update.type());
            assertNull(update.winnerId());
        }

        @Test
        void aKingWhoLeftHisTeamCannotHandItToThem() {
            crownAlice();
            // Alice left red mid-reign; a red player kills them.
            assertEquals(Type.DIED, manager.kingDied(carol, red, null).orElseThrow().type());
        }

        @Test
        void aKingWhoJoinedATeamCannotBeKilledForThem() {
            crownAlice();
            // Alice joined blue mid-reign; a blue player kills them.
            assertEquals(Type.DIED, manager.kingDied(bob, blue, blue).orElseThrow().type());
        }

        @Test
        void aTeamlessKingCanBeKilledByAnyone() {
            assertTrue(manager.open(ktk()));
            draw.next = 0;
            manager.crown(List.of(new Candidate(carol, null), new Candidate(bob, blue)));

            assertEquals(Type.KILLED, manager.kingDied(alice, null, null).orElseThrow().type(),
                    "two players without a team are not teammates");
        }

        @Test
        void aDeathWithNoKillerHasNoWinner() {
            crownAlice();
            KingUpdate update = manager.kingDied(null, null, red).orElseThrow();
            assertEquals(Type.DIED, update.type());
            assertNull(update.winnerId());
        }

        @Test
        void killingHimselfWinsNothing() {
            crownAlice();
            assertEquals(Type.DIED, manager.kingDied(alice, red, red).orElseThrow().type());
        }

        @Test
        void aDeathAfterTheReignIsNotTheKingsDeath() {
            crownAlice();
            advanceSeconds(600);
            manager.tick(true);
            assertTrue(manager.kingDied(bob, blue, red).isEmpty());
        }
    }

    @Nested
    class OtherEndings {

        @Test
        void loggingOutEndsItWithNoWinner() {
            crownAlice();
            KingUpdate update = manager.kingQuit().orElseThrow();

            assertEquals(Type.FLED, update.type());
            assertEquals(alice, update.kingId(), "so their items can be handed back at their next login");
            assertNull(update.winnerId());
            assertTrue(manager.getCurrent().isEmpty());
        }

        @Test
        void staffCanStopItCrownedOrNot() {
            crownAlice();
            KingUpdate stopped = manager.stop().orElseThrow();
            assertEquals(Type.STOPPED, stopped.type());
            assertEquals(alice, stopped.kingId());

            assertTrue(manager.open(ktk()));
            KingUpdate beforeCrowning = manager.stop().orElseThrow();
            assertNull(beforeCrowning.kingId(), "nobody to give items back to");
            assertTrue(manager.stop().isEmpty());
        }

        @Test
        void theServerCanCallItOffWithItsOwnReason() {
            assertTrue(manager.open(ktk()));
            KingUpdate update = manager.cancel(KingMessages.CANCELLED_NO_SPOT).orElseThrow();
            assertEquals(Type.CANCELLED, update.type());
            assertEquals(KingMessages.CANCELLED_NO_SPOT, update.messageKey());
        }

        @Test
        void everyEndingIsFinal() {
            for (Type type : Type.values()) {
                boolean ending = switch (type) {
                    case SURVIVED, KILLED, DIED, FLED, STOPPED, CANCELLED -> true;
                    case CROWNED, PROGRESS, LEFT_ZONE, RETURNED, PENALTY -> false;
                };
                assertEquals(ending, type.isEnding(), type.name());
            }
        }
    }

    @Nested
    class Schedule {

        @Test
        void theFirstCheckAfterAStartFiresNothing() {
            settings = new KingSettings(true, UTC,
                    List.of(definition(600, 2, List.of(), List.of(LocalTime.of(11, 59, 30)))));
            assertTrue(manager.dueDefinitions().isEmpty(), "a restart does not fire a time already passed");
        }

        @Test
        void aScheduledTimeInsideTheWindowIsDue() {
            settings = new KingSettings(true, UTC,
                    List.of(definition(600, 2, List.of(), List.of(LocalTime.of(12, 0, 30)))));
            manager.dueDefinitions();
            advanceSeconds(20);
            assertTrue(manager.dueDefinitions().isEmpty());
            advanceSeconds(20);
            assertEquals(List.of("ktk"), manager.dueDefinitions().stream().map(KingEventDefinition::id).toList());
            advanceSeconds(20);
            assertTrue(manager.dueDefinitions().isEmpty(), "due once");
        }

        @Test
        void nothingIsDueWhileEventsAreDisabled() {
            settings = new KingSettings(false, UTC,
                    List.of(definition(600, 2, List.of(), List.of(LocalTime.of(12, 0, 30)))));
            manager.dueDefinitions();
            advanceSeconds(60);
            assertTrue(manager.dueDefinitions().isEmpty());
        }

        @Test
        void theNextOccurrenceIsReadInTheConfiguredZone() {
            settings = new KingSettings(true, UTC,
                    List.of(definition(600, 2, List.of(), List.of(LocalTime.of(18, 0)))));
            Optional<ZonedDateTime> next = manager.getNextOccurrence(ktk());
            assertEquals(ZonedDateTime.of(2026, 9, 11, 18, 0, 0, 0, UTC), next.orElseThrow());
        }
    }

    @Nested
    class Definitions {

        @Test
        void aReignMustLastAndNeedsSomebody() {
            assertThrows(IllegalArgumentException.class, () -> definition(0, 2, List.of(), List.of()));
            assertThrows(IllegalArgumentException.class, () -> definition(600, 0, List.of(), List.of()));
        }

        @Test
        void thePenaltyRejectsValuesThatCannotWork() {
            assertThrows(IllegalArgumentException.class, () -> new OutsidePenalty(-1L, 1.0, 1, 10L, 5));
            assertThrows(IllegalArgumentException.class, () -> new OutsidePenalty(3L, -1.0, 1, 10L, 5));
            assertThrows(IllegalArgumentException.class, () -> new OutsidePenalty(3L, 1.0, 0, 10L, 5));
            assertThrows(IllegalArgumentException.class, () -> new OutsidePenalty(3L, 1.0, 1, 0L, 5));
            assertThrows(IllegalArgumentException.class, () -> new OutsidePenalty(3L, 1.0, 3, 10L, 2));
        }

        @Test
        void aZeroGraceBitesAtOnce() {
            OutsidePenalty penalty = new OutsidePenalty(0L, 1.0, 2, 5L, 4);
            assertTrue(penalty.appliesAfter(0L));
            assertEquals(2, penalty.witherLevelAfter(0L));
            assertEquals(3, penalty.witherLevelAfter(5L));
        }

        @Test
        void kitMapsCannotBeChangedFromOutside() {
            Map<String, Integer> effects = new java.util.HashMap<>(Map.of("speed", 2));
            KingKit kit = new KingKit(List.of(), effects);
            effects.put("strength", 5);
            assertEquals(Map.of("speed", 2), kit.effects());
            assertThrows(UnsupportedOperationException.class, () -> kit.effects().put("haste", 1));
        }
    }

    // ------------------------------------------------------------------

    /** Draws whatever index the test sets, and records the bound it was asked for. */
    private static final class FixedDraw implements RandomGenerator {

        int next;
        int lastBound;

        @Override
        public long nextLong() {
            throw new UnsupportedOperationException("only nextInt(bound) is expected");
        }

        @Override
        public int nextInt(int bound) {
            lastBound = bound;
            return next;
        }
    }
}
