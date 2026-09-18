package com.lawkeys.hcfcore.events;

import com.lawkeys.hcfcore.util.Cuboid;
import com.lawkeys.hcfcore.util.Durations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rule-level tests for the capture events of ARCHITECTURE.md section 9, family A.
 *
 * <p>Every one of these plays a whole KOTH out in microseconds by moving an
 * injected clock and describing who is standing in the zone. That is the point of
 * keeping the rule engine free of the server API.
 */
class EventTest {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final Cuboid ZONE = Cuboid.between("world", 0, 60, 0, 15, 80, 15);

    private final AtomicLong clock = new AtomicLong(1_000_000L);

    private EventSettings settings;
    private EventManager events;

    private UUID alpha;
    private UUID bravo;
    private UUID player;

    @BeforeEach
    void setUp() {
        alpha = UUID.randomUUID();
        bravo = UUID.randomUUID();
        player = UUID.randomUUID();
        settings = withDefinitions(koth(60, ContestPolicy.RESET));
        events = new EventManager(() -> settings, clock::get);
    }

    // --- helpers ----------------------------------------------------------

    private static CaptureEventDefinition koth(long captureSeconds, ContestPolicy policy) {
        return koth(captureSeconds, policy, List.of(), List.of());
    }

    private static CaptureEventDefinition koth(long captureSeconds, ContestPolicy policy,
                                               List<Long> marks, List<LocalTime> schedule) {
        return new CaptureEventDefinition("koth", "&6KOTH", ZONE, captureSeconds, policy,
                0L, marks, schedule, List.of());
    }

    private EventSettings withDefinitions(CaptureEventDefinition... definitions) {
        return new EventSettings(true, 1L, UTC, true, true, List.of(definitions));
    }

    private void advance(long seconds) {
        clock.addAndGet(seconds * 1000L);
    }

    /** Ticks with the given occupants and returns what the manager reported. */
    private List<EventUpdate> tick(Occupant... occupants) {
        return events.tick(Map.of("koth", List.of(occupants)));
    }

    /** Ticks {@code times} times, one second apart, with the same occupants. */
    private List<EventUpdate> tickFor(long times, Occupant... occupants) {
        List<EventUpdate> all = new ArrayList<>();
        for (long i = 0; i < times; i++) {
            advance(1);
            all.addAll(tick(occupants));
        }
        return all;
    }

    private static boolean has(List<EventUpdate> updates, EventUpdate.Type type) {
        return updates.stream().anyMatch(u -> u.type() == type);
    }

    private static long count(List<EventUpdate> updates, EventUpdate.Type type) {
        return updates.stream().filter(u -> u.type() == type).count();
    }

    private void startKoth() {
        assertTrue(events.start(settings.find("koth").orElseThrow()).isPresent());
    }

    // ------------------------------------------------------------------

    @Nested
    class Zones {

        @Test
        void boundsAreInclusiveSoTheRegionMatchesWhatWasConfigured() {
            assertTrue(ZONE.contains("world", 0, 60, 0));
            assertTrue(ZONE.contains("world", 15.9, 80.9, 15.9), "the block at maxX is inside");
            assertFalse(ZONE.contains("world", 16, 70, 8));
        }

        @Test
        void cornersMayBeConfiguredInEitherOrder() {
            Cuboid reversed = Cuboid.between("world", 15, 80, 15, 0, 60, 0);
            assertEquals(ZONE, reversed);
        }

        @Test
        void negativeCoordinatesLandInTheRightBlock() {
            // A cast truncates towards zero and would put -0.5 in block 0, widening
            // the zone by one block on its negative side.
            Cuboid zone = Cuboid.between("world", -5, 0, -5, -1, 10, -1);
            assertTrue(zone.contains("world", -0.5, 5, -0.5), "-0.5 is in block -1, which is inside");
            assertFalse(zone.contains("world", 0.5, 5, 0.5));
        }

        @Test
        void aZoneIsBoundToItsWorld() {
            assertFalse(ZONE.contains("world_nether", 5, 70, 5));
        }
    }

    @Nested
    class Capturing {

        @Test
        void holdingTheZoneUninterruptedWinsTheEvent() {
            startKoth();

            List<EventUpdate> updates = tickFor(60, Occupant.of(player, alpha));

            assertTrue(has(updates, EventUpdate.Type.CAPTURE_BEGAN));
            assertTrue(has(updates, EventUpdate.Type.CAPTURED));
            assertFalse(events.isActive("koth"), "a captured event stops running");

            EventUpdate captured = updates.stream()
                    .filter(u -> u.type() == EventUpdate.Type.CAPTURED).findFirst().orElseThrow();
            assertEquals(alpha, captured.teamId(), "the holder wins, and the update names them");
        }

        @Test
        void anEmptyZoneNeverCaptures() {
            startKoth();

            tickFor(120);

            assertTrue(events.isActive("koth"), "nobody held it, so nobody won it");
        }

        @Test
        void aTeamlessPlayerCannotHoldTheZone() {
            startKoth();

            tickFor(120, Occupant.of(player, null));

            assertTrue(events.isActive("koth"));
        }

        @Test
        void severalMembersOfTheSameTeamStillCountAsOneHolder() {
            startKoth();

            tickFor(60, Occupant.of(player, alpha), Occupant.of(UUID.randomUUID(), alpha));

            assertFalse(events.isActive("koth"), "bringing friends does not stop you capturing");
        }
    }

    @Nested
    class Contesting {

        @Test
        void anEnemyInTheZoneFreezesTheCountdownWithoutWipingIt() {
            // This test used to be called "freezes the countdown" and only asserted
            // that the event was still running - which is true whether the progress
            // is frozen OR erased. The weak assertion is what let the bug through:
            // under RESET the code was wiping the progress the moment an enemy
            // stepped in, contradicting both this message and FEATURES.md.
            startKoth();
            tickFor(30, Occupant.of(player, alpha));
            assertEquals(30, events.getActiveEvent("koth").orElseThrow().getRemainingSeconds());

            List<EventUpdate> updates =
                    tickFor(120, Occupant.of(player, alpha), Occupant.of(UUID.randomUUID(), bravo));

            assertTrue(has(updates, EventUpdate.Type.CONTESTED));
            assertTrue(events.isActive("koth"), "a contested zone can never be won");
            assertEquals(30, events.getActiveEvent("koth").orElseThrow().getRemainingSeconds(),
                    "frozen means frozen: two minutes of being contested cost the holder nothing");
        }

        @Test
        void theSameTeamResumingAfterAContestIsNotAnnouncedAgain() {
            startKoth();
            tickFor(20, Occupant.of(player, alpha));
            tickFor(5, Occupant.of(player, alpha), Occupant.of(UUID.randomUUID(), bravo));

            List<EventUpdate> updates = tickFor(10, Occupant.of(player, alpha));

            assertEquals(0, count(updates, EventUpdate.Type.CAPTURE_BEGAN),
                    "alpha never stopped being the holder, so there is nothing to announce");
            assertEquals(30, events.getActiveEvent("koth").orElseThrow().getRemainingSeconds(),
                    "the contested seconds cost nothing and the capture picked up where it was");
        }

        @Test
        void aDifferentTeamTakingOverAfterAContestStillResets() {
            // The other half of the rule: freezing protects the holder, it does not
            // protect a team that actually loses the zone to someone else.
            startKoth();
            tickFor(40, Occupant.of(player, alpha));
            tickFor(5, Occupant.of(player, alpha), Occupant.of(UUID.randomUUID(), bravo));

            tickFor(1, Occupant.of(UUID.randomUUID(), bravo));

            assertEquals(59, events.getActiveEvent("koth").orElseThrow().getRemainingSeconds(),
                    "alpha's progress is gone and bravo's has begun");
        }

        @Test
        void aZoneThatEmptiesAfterAContestStillLosesTheProgressUnderReset() {
            startKoth();
            tickFor(40, Occupant.of(player, alpha));
            tickFor(5, Occupant.of(player, alpha), Occupant.of(UUID.randomUUID(), bravo));

            List<EventUpdate> updates = tickFor(1);

            assertTrue(has(updates, EventUpdate.Type.CONTROL_LOST),
                    "being pushed off the zone is reported even when a contest preceded it");
            assertEquals(60, events.getActiveEvent("koth").orElseThrow().getRemainingSeconds());
        }

        @Test
        void contestedIsAnnouncedOnceNotEveryTick() {
            startKoth();

            List<EventUpdate> updates =
                    tickFor(30, Occupant.of(player, alpha), Occupant.of(UUID.randomUUID(), bravo));

            assertEquals(1, count(updates, EventUpdate.Type.CONTESTED),
                    "thirty ticks of a brawl must not produce thirty broadcasts");
        }

        @Test
        void twoAlliedTeamsStillContestEachOther() {
            // Deliberate simplification for this first iteration: capture is per
            // team, and the engine does not ask the team module about alliances.
            // Documented in events.yml as a point to confirm.
            startKoth();

            tickFor(120, Occupant.of(player, alpha), Occupant.of(UUID.randomUUID(), bravo));

            assertTrue(events.isActive("koth"));
        }

        @Test
        void aTeamlessPlayerContestsByDefault() {
            startKoth();

            tickFor(120, Occupant.of(player, alpha), Occupant.of(UUID.randomUUID(), null));

            assertTrue(events.isActive("koth"), "everybody's enemy blocks the capture");
        }

        @Test
        void aTeamlessPlayerCanBeMadeHarmless() {
            settings = new EventSettings(true, 1L, UTC, true, false,
                    List.of(koth(60, ContestPolicy.RESET)));
            startKoth();

            tickFor(60, Occupant.of(player, alpha), Occupant.of(UUID.randomUUID(), null));

            assertFalse(events.isActive("koth"), "with the knob off, teams cap around them");
        }
    }

    @Nested
    class LosingControl {

        @Test
        void theResetPolicyPutsTheCountdownBackToFull() {
            startKoth();
            tickFor(59, Occupant.of(player, alpha));
            assertEquals(1, events.getActiveEvent("koth").orElseThrow().getRemainingSeconds());

            tickFor(1); // knocked off the zone with one second to go

            assertEquals(60, events.getActiveEvent("koth").orElseThrow().getRemainingSeconds(),
                    "the classic, brutal KOTH: nine minutes of holding count for nothing");
        }

        @Test
        void thePausePolicyResumesWhereItStopped() {
            settings = withDefinitions(koth(60, ContestPolicy.PAUSE));
            startKoth();
            tickFor(40, Occupant.of(player, alpha));

            tickFor(10); // the zone empties for ten seconds

            assertEquals(20, events.getActiveEvent("koth").orElseThrow().getRemainingSeconds(),
                    "the countdown froze rather than resetting");

            tickFor(20, Occupant.of(player, alpha));
            assertFalse(events.isActive("koth"));
        }

        @Test
        void underPauseANewHolderInheritsTheProgress() {
            settings = withDefinitions(koth(60, ContestPolicy.PAUSE));
            startKoth();
            tickFor(50, Occupant.of(player, alpha));

            tickFor(10, Occupant.of(UUID.randomUUID(), bravo));

            assertFalse(events.isActive("koth"), "bravo finished what alpha started");
        }

        @Test
        void underResetANewHolderStartsFromScratch() {
            startKoth();
            tickFor(50, Occupant.of(player, alpha));

            tickFor(10, Occupant.of(UUID.randomUUID(), bravo));

            assertTrue(events.isActive("koth"));
            assertEquals(50, events.getActiveEvent("koth").orElseThrow().getRemainingSeconds(),
                    "bravo held for ten of the sixty seconds they now need");
        }

        @Test
        void losingControlIsReportedOnceWithTheTimeThatIsLeft() {
            settings = withDefinitions(koth(60, ContestPolicy.PAUSE));
            startKoth();
            tickFor(20, Occupant.of(player, alpha));

            List<EventUpdate> updates = tickFor(5);

            assertEquals(1, count(updates, EventUpdate.Type.CONTROL_LOST));
        }
    }

    @Nested
    class Announcements {

        @Test
        void eachMilestoneIsAnnouncedExactlyOnce() {
            settings = withDefinitions(koth(60, ContestPolicy.PAUSE, List.of(30L, 10L), List.of()));
            startKoth();

            List<EventUpdate> updates = tickFor(55, Occupant.of(player, alpha));

            assertEquals(2, count(updates, EventUpdate.Type.PROGRESS));
        }

        @Test
        void aMarkAtOrAboveTheFullDurationIsNotAnnounced() {
            // Otherwise it would fire the instant the event opens, which is noise.
            settings = withDefinitions(koth(60, ContestPolicy.PAUSE, List.of(60L, 90L), List.of()));
            startKoth();

            List<EventUpdate> updates = tickFor(5, Occupant.of(player, alpha));

            assertEquals(0, count(updates, EventUpdate.Type.PROGRESS));
        }

        @Test
        void milestonesAreAnnouncedAgainAfterAReset() {
            settings = withDefinitions(koth(60, ContestPolicy.RESET, List.of(30L), List.of()));
            startKoth();
            List<EventUpdate> first = tickFor(35, Occupant.of(player, alpha));
            assertEquals(1, count(first, EventUpdate.Type.PROGRESS));

            tickFor(1); // knocked off, countdown back to full
            List<EventUpdate> second = tickFor(35, Occupant.of(player, alpha));

            assertEquals(1, count(second, EventUpdate.Type.PROGRESS),
                    "a fresh attempt deserves its own warnings");
        }
    }

    @Nested
    class TimeLimit {

        @Test
        void anEventCanExpireWithNoWinner() {
            CaptureEventDefinition definition = new CaptureEventDefinition("koth", "&6KOTH", ZONE,
                    600L, ContestPolicy.RESET, 30L, List.of(), List.of(), List.of());
            settings = withDefinitions(definition);
            startKoth();

            List<EventUpdate> updates = tickFor(31, Occupant.of(player, alpha));

            assertTrue(has(updates, EventUpdate.Type.EXPIRED));
            assertFalse(events.isActive("koth"));
        }

        @Test
        void zeroMeansItRunsUntilSomebodyWins() {
            startKoth();

            tickFor(3600);

            assertTrue(events.isActive("koth"));
        }
    }

    @Nested
    class Scheduling {

        private EventSettings scheduledAt(LocalTime... times) {
            return new EventSettings(true, 1L, UTC, true, true,
                    List.of(koth(60, ContestPolicy.RESET, List.of(), List.of(times))));
        }

        /** @return epoch millis for that UTC time on 2026-09-01 */
        private long utc(int hour, int minute, int second) {
            return java.time.LocalDate.of(2026, 9, 1)
                    .atTime(hour, minute, second).atZone(UTC).toInstant().toEpochMilli();
        }

        @Test
        void theFirstTickAfterAStartFiresNothing() {
            settings = scheduledAt(LocalTime.of(18, 0));
            clock.set(utc(18, 5, 0));

            assertTrue(events.tick(Map.of()).isEmpty(),
                    "restarting at 18:05 must not immediately open the 18:00 event");
            assertFalse(events.isActive("koth"));
        }

        @Test
        void anEventOpensWhenTheWindowCrossesItsTime() {
            settings = scheduledAt(LocalTime.of(18, 0));
            clock.set(utc(17, 59, 59));
            events.tick(Map.of()); // records the window

            clock.set(utc(18, 0, 1));
            List<EventUpdate> updates = events.tick(Map.of());

            assertTrue(has(updates, EventUpdate.Type.STARTED));
            assertTrue(events.isActive("koth"));
        }

        @Test
        void aWindowSpanningMidnightStillCatchesALateTime() {
            settings = scheduledAt(LocalTime.of(23, 59));
            clock.set(utc(23, 58, 0));
            events.tick(Map.of());

            clock.set(utc(23, 59, 0) + 120_000L); // 00:01 the next day
            List<EventUpdate> updates = events.tick(Map.of());

            assertTrue(has(updates, EventUpdate.Type.STARTED),
                    "the day rolling over must not swallow a scheduled start");
        }

        @Test
        void anEventAlreadyRunningIsNotOpenedTwice() {
            settings = scheduledAt(LocalTime.of(18, 0));
            clock.set(utc(17, 59, 59));
            events.tick(Map.of());
            startKoth();

            clock.set(utc(18, 0, 1));
            List<EventUpdate> updates = events.tick(Map.of());

            assertFalse(has(updates, EventUpdate.Type.STARTED));
        }

        @Test
        void theNextOccurrenceIsTheSoonestOfTheDay() {
            settings = scheduledAt(LocalTime.of(21, 0), LocalTime.of(18, 0));
            CaptureEventDefinition definition = settings.find("koth").orElseThrow();

            var next = events.getNextOccurrence(definition, utc(12, 0, 0)).orElseThrow();

            assertEquals(18, next.getHour());
        }

        @Test
        void afterTheLastTimeOfTheDayTheNextOccurrenceIsTomorrow() {
            settings = scheduledAt(LocalTime.of(18, 0));
            CaptureEventDefinition definition = settings.find("koth").orElseThrow();

            var next = events.getNextOccurrence(definition, utc(19, 0, 0)).orElseThrow();

            assertEquals(2, next.getDayOfMonth());
        }

        @Test
        void anEventWithNoScheduleHasNoNextOccurrence() {
            assertTrue(events.getNextOccurrence(settings.find("koth").orElseThrow()).isEmpty());
        }
    }

    @Nested
    class StaffControl {

        @Test
        void anEventCannotBeStartedTwice() {
            startKoth();
            assertTrue(events.start(settings.find("koth").orElseThrow()).isEmpty());
            assertEquals(1, events.getActiveCount());
        }

        @Test
        void stoppingReportsTheEventAndLeavesNoWinner() {
            startKoth();

            EventUpdate update = events.stop("koth").orElseThrow();

            assertEquals(EventUpdate.Type.STOPPED, update.type());
            assertFalse(events.isActive("koth"));
        }

        @Test
        void stoppingSomethingThatIsNotRunningIsNotAnError() {
            assertTrue(events.stop("koth").isEmpty());
            assertTrue(events.stop("nonexistent").isEmpty());
        }

        @Test
        void concurrentStartsProduceExactlyOneAnnouncement() throws Exception {
            // The insert has to be one atomic step, not containsKey-then-put. Only
            // the main thread starts events today, but that is a calling convention
            // rather than something the type enforces - and the cache is concurrent
            // precisely so it does not have to be.
            CaptureEventDefinition definition = settings.find("koth").orElseThrow();
            int threads = 16;
            var started = new java.util.concurrent.atomic.AtomicInteger();
            var ready = new java.util.concurrent.CountDownLatch(1);
            var done = new java.util.concurrent.CountDownLatch(threads);

            var pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
            try {
                for (int i = 0; i < threads; i++) {
                    pool.execute(() -> {
                        try {
                            ready.await();
                            if (events.start(definition).isPresent()) {
                                started.incrementAndGet();
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            done.countDown();
                        }
                    });
                }
                ready.countDown();
                assertTrue(done.await(10, java.util.concurrent.TimeUnit.SECONDS));
            } finally {
                pool.shutdownNow();
            }

            assertEquals(1, started.get(), "sixteen racing callers, one announced start");
            assertEquals(1, events.getActiveCount());
        }

        @Test
        void eventIdsAreCaseInsensitive() {
            startKoth();
            assertTrue(events.isActive("KOTH"));
            assertTrue(events.stop("Koth").isPresent());
        }
    }

    /**
     * ARCHITECTURE.md section 9 asks for one thing explicitly: that Citadel and
     * other KOTH variants fit the family-A abstraction without forcing it. This is
     * that check - a 30-minute continuous hold is a config value, not a subclass.
     */
    @Nested
    class CitadelIsJustConfiguration {

        @Test
        void aThirtyMinuteContinuousHoldNeedsNoCodeOfItsOwn() {
            CaptureEventDefinition citadel = new CaptureEventDefinition("koth", "&5Citadel", ZONE,
                    1800L, ContestPolicy.RESET, 0L, List.of(), List.of(), List.of());
            settings = withDefinitions(citadel);
            startKoth();

            tickFor(1799, Occupant.of(player, alpha));
            assertTrue(events.isActive("koth"), "one second short is still short");

            tickFor(1, Occupant.of(player, alpha));
            assertFalse(events.isActive("koth"));
        }

        @Test
        void andItsLengthMakesTheResetPolicyBite() {
            CaptureEventDefinition citadel = new CaptureEventDefinition("koth", "&5Citadel", ZONE,
                    1800L, ContestPolicy.RESET, 0L, List.of(), List.of(), List.of());
            settings = withDefinitions(citadel);
            startKoth();
            tickFor(1700, Occupant.of(player, alpha));

            tickFor(1, Occupant.of(UUID.randomUUID(), bravo));

            // 1799 rather than 1800, and that is the correct answer: taking the zone
            // wipes alpha's 1700 seconds of progress *and* starts bravo's own capture
            // in the same tick. Expecting a clean 1800 would mean the new holder stood
            // there for a second doing nothing.
            assertEquals(1799, events.getActiveEvent("koth").orElseThrow().getRemainingSeconds(),
                    "alpha's progress is gone and bravo's has begun");
        }
    }

    @Nested
    class Definitions {

        @Test
        void aCaptureDurationMustBePositive() {
            assertThrows(IllegalArgumentException.class, () -> koth(0, ContestPolicy.RESET));
        }

        @Test
        void durationsAreFormattedForPlayers() {
            assertEquals("45s", Durations.format(45));
            assertEquals("1m", Durations.format(60));
            assertEquals("10m 30s", Durations.format(630));
            assertEquals("1h", Durations.format(3600));
            assertEquals("1h 30m", Durations.format(5400));
        }

        @Test
        void aDisabledModuleDoesNothingOnTick() {
            settings = new EventSettings(false, 1L, UTC, true, true,
                    List.of(koth(60, ContestPolicy.RESET)));
            startKoth();

            assertTrue(tickFor(120, Occupant.of(player, alpha)).isEmpty());
            assertTrue(events.isActive("koth"), "it is frozen, not cancelled");
        }

        @Test
        void timeSpentDisabledIsNotCreditedWhenTheModuleComesBack() {
            // The assertion the test above was missing. "Frozen, not cancelled" is
            // only half the claim: the freeze also has to survive re-enabling. Without
            // this, the whole disabled period arrived as one elapsed chunk and handed
            // the KOTH to whoever was standing in the zone at that moment.
            settings = new EventSettings(false, 1L, UTC, true, true,
                    List.of(koth(60, ContestPolicy.RESET)));
            startKoth();
            tickFor(120, Occupant.of(player, alpha));

            settings = withDefinitions(koth(60, ContestPolicy.RESET)); // re-enabled
            tickFor(1, Occupant.of(player, alpha));

            assertTrue(events.isActive("koth"),
                    "two disabled minutes must not win a one-minute capture");
            assertEquals(59, events.getActiveEvent("koth").orElseThrow().getRemainingSeconds(),
                    "exactly one second of real capture has been credited");
        }

        @Test
        void aScheduledEventDoesNotFireForTimesThatPassedWhileDisabled() {
            CaptureEventDefinition scheduled = koth(60, ContestPolicy.RESET,
                    List.of(), List.of(java.time.LocalTime.of(18, 0)));
            settings = new EventSettings(false, 1L, UTC, true, true, List.of(scheduled));
            clock.set(java.time.LocalDate.of(2026, 9, 1)
                    .atTime(17, 59, 0).atZone(UTC).toInstant().toEpochMilli());
            events.tick(Map.of());

            clock.set(java.time.LocalDate.of(2026, 9, 1)
                    .atTime(18, 30, 0).atZone(UTC).toInstant().toEpochMilli());
            events.tick(Map.of());

            settings = new EventSettings(true, 1L, UTC, true, true, List.of(scheduled));
            List<EventUpdate> updates = events.tick(Map.of());

            assertFalse(has(updates, EventUpdate.Type.STARTED),
                    "18:00 passed while the module was off; re-enabling at 18:30 must not open it");
        }

        @Test
        void everyUpdateCarriesALanguageKeyRatherThanASentence() {
            startKoth();
            List<EventUpdate> updates = tickFor(60, Occupant.of(player, alpha));

            for (EventUpdate update : updates) {
                assertNotNull(update.messageKey());
                assertTrue(update.messageKey().startsWith("events."),
                        update.messageKey() + " must be a language key");
            }
        }
    }
}
