package com.lawkeys.hcfcore.phase;

import com.lawkeys.hcfcore.claim.RaidabilityPolicy;
import com.lawkeys.hcfcore.phase.PhaseUpdate.Type;
import com.lawkeys.hcfcore.pvp.DeathbanPolicy.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SOTW and EOTW, played without a server: the rules the project owner set on
 * 11/09/2026, the scheduled windows, and restarts in the middle of either.
 */
class PhaseManagerTest {

    private static final long SECOND = 1_000L;
    private static final long HOUR = 3_600L;
    private static final ZoneId UTC = ZoneId.of("UTC");

    private final AtomicLong now = new AtomicLong(1_800_000_000_000L);
    private PhaseSettings settings;
    private RecordingStore store;
    private PhaseManager phases;

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        settings = new PhaseSettings(2 * HOUR, 0L, 0L, HOUR, List.of(3_600L, 600L, 60L), UTC, PhaseSettings.PurgeRules.NONE);
        store = new RecordingStore();
        phases = new PhaseManager(() -> settings, store, now::get);
    }

    private void advance(long seconds) {
        now.addAndGet(seconds * SECOND);
    }

    private static List<Type> types(List<PhaseUpdate> updates) {
        return updates.stream().map(PhaseUpdate::type).toList();
    }

    // ------------------------------------------------------------------

    /** SOTW: no PvP, no deathban, no DTR lost - and players may opt out, one way. */
    @Nested
    class DuringSotw {

        @BeforeEach
        void start() {
            PhaseResult result = phases.startSotw(2 * HOUR);
            assertTrue(result.success());
            assertEquals(Type.SOTW_STARTED, result.broadcast().orElseThrow().type());
        }

        @Test
        void nobodyCanHitAnybody() {
            assertEquals(Optional.of(PhaseMessages.SOTW_YOU_ARE_PROTECTED), phases.combatRefusal(alice, bob));
        }

        @Test
        void enablingPvpLetsYouFightOnlyThoseWhoDidTheSame() {
            assertTrue(phases.enablePvp(alice).success());
            assertEquals(Optional.of(PhaseMessages.SOTW_VICTIM_PROTECTED), phases.combatRefusal(alice, bob),
                    "Bob is still protected");
            assertEquals(Optional.of(PhaseMessages.SOTW_YOU_ARE_PROTECTED), phases.combatRefusal(bob, alice),
                    "and Bob cannot hit Alice from behind their own protection");

            phases.enablePvp(bob);
            assertTrue(phases.combatRefusal(alice, bob).isEmpty());
            assertTrue(phases.combatRefusal(bob, alice).isEmpty());
        }

        @Test
        void enablingIsOneWayAndOnce() {
            phases.enablePvp(alice);
            PhaseResult again = phases.enablePvp(alice);
            assertFalse(again.success());
            assertEquals(PhaseMessages.SOTW_ALREADY_ENABLED, again.messageKey());
        }

        @Test
        void aDeathBansNobodyAndCostsNoDtr() {
            assertEquals(Rule.NONE, phases.deathbanRule());
            assertFalse(phases.deathsCostDtr());
        }

        @Test
        void claimingAndRaidsAreUntouched() {
            assertTrue(phases.claimingRefusal().isEmpty());
            assertFalse(phases.raidability(RaidabilityPolicy.NEVER).isRaidable(UUID.randomUUID()));
        }

        @Test
        void itCannotBeStartedTwice() {
            PhaseResult again = phases.startSotw(HOUR);
            assertFalse(again.success());
            assertEquals(PhaseMessages.SOTW_ALREADY_ACTIVE, again.messageKey());
        }

        @Test
        void whenItRunsOutEverythingIsBackAndItIsAnnouncedOnce() {
            phases.enablePvp(alice);
            advance(2 * HOUR);

            List<PhaseUpdate> updates = phases.tick();
            assertTrue(types(updates).contains(Type.SOTW_ENDED));
            assertFalse(phases.isSotw());
            assertTrue(phases.combatRefusal(alice, bob).isEmpty());
            assertEquals(Rule.USUAL, phases.deathbanRule());
            assertTrue(phases.deathsCostDtr());
            assertFalse(phases.hasEnabledPvp(alice), "the choice belonged to that SOTW");

            assertFalse(types(phases.tick()).contains(Type.SOTW_ENDED));
        }

        @Test
        void staffStoppingItIsAnnouncedOnceNotTwice() {
            PhaseResult stopped = phases.stopSotw();
            assertEquals(Type.SOTW_STOPPED, stopped.broadcast().orElseThrow().type());
            assertFalse(phases.isSotw());
            assertTrue(phases.tick().isEmpty(), "no 'SOTW is over' on top of 'stopped by staff'");
        }

        @Test
        void milestonesAreAnnouncedOnceAndNeverAtTheStart() {
            // Started with 2h: the 1h mark is ahead, nothing above it fires.
            assertTrue(phases.tick().isEmpty());
            advance(HOUR);
            assertEquals(List.of(Type.SOTW_PROGRESS), types(phases.tick()));
            advance(1);
            assertTrue(phases.tick().isEmpty());
            advance(HOUR - 600 - 1);
            assertEquals(List.of(Type.SOTW_PROGRESS), types(phases.tick()));
        }

        @Test
        void noEotwWhileItRuns() {
            PhaseResult eotw = phases.startEotw();
            assertFalse(eotw.success());
            assertEquals(PhaseMessages.EOTW_DURING_SOTW, eotw.messageKey());
        }
    }

    /** EOTW: every team raidable, no new player claims, a death bans until the map ends. */
    @Nested
    class DuringEotw {

        @BeforeEach
        void start() {
            assertEquals(Type.EOTW_STARTED, phases.startEotw().broadcast().orElseThrow().type());
        }

        @Test
        void everyTeamIsRaidableWhateverItsDtr() {
            UUID team = UUID.randomUUID();
            RaidabilityPolicy withEotw = phases.raidability(RaidabilityPolicy.NEVER);
            assertTrue(withEotw.isRaidable(team));

            phases.stopEotw();
            assertFalse(withEotw.isRaidable(team), "the DTR decides again once it is over");
        }

        @Test
        void claimingIsClosed() {
            assertEquals(Optional.of(PhaseMessages.EOTW_NO_CLAIMS), phases.claimingRefusal());
        }

        @Test
        void aDeathBansUntilTheMapEnds() {
            assertEquals(Rule.UNTIL_MAP_END, phases.deathbanRule());
        }

        @Test
        void pvpAndDtrFollowTheUsualRules() {
            assertTrue(phases.combatRefusal(alice, bob).isEmpty());
            assertTrue(phases.deathsCostDtr());
        }

        @Test
        void noSotwWhileItRunsAndNoSecondEotw() {
            assertEquals(PhaseMessages.SOTW_DURING_EOTW, phases.startSotw(HOUR).messageKey());
            assertEquals(PhaseMessages.EOTW_ALREADY_ACTIVE, phases.startEotw().messageKey());
        }

        @Test
        void itLastsUntilStaffStopIt() {
            advance(30L * 24 * HOUR);
            assertTrue(phases.isEotw());
            assertEquals(Type.EOTW_STOPPED, phases.stopEotw().broadcast().orElseThrow().type());
            assertFalse(phases.isEotw());
            assertEquals(PhaseMessages.EOTW_NOT_ACTIVE, phases.stopEotw().messageKey());
        }
    }

    @Nested
    class OutsideEither {

        @Test
        void everythingFollowsTheUsualRules() {
            assertTrue(phases.combatRefusal(alice, bob).isEmpty());
            assertEquals(Rule.USUAL, phases.deathbanRule());
            assertTrue(phases.deathsCostDtr());
            assertTrue(phases.claimingRefusal().isEmpty());
            assertFalse(phases.raidability(RaidabilityPolicy.NEVER).isRaidable(UUID.randomUUID()));
        }

        @Test
        void thereIsNothingToEnableOrStop() {
            assertEquals(PhaseMessages.SOTW_NOT_ACTIVE, phases.enablePvp(alice).messageKey());
            assertEquals(PhaseMessages.SOTW_NOT_ACTIVE, phases.stopSotw().messageKey());
        }

        @Test
        void aDurationMustBePositive() {
            assertEquals(PhaseMessages.INVALID_DURATION, phases.startSotw(0L).messageKey());
        }
    }

    /**
     * A scheduled SOTW is a window, from its date to its date plus its duration; a
     * scheduled EOTW may start up to its start window late. Each is acted on once.
     */
    @Nested
    class Schedule {

        private final long at = 1_800_000_600_000L; // ten minutes after "now"

        @Test
        void aScheduledSotwStartsAtItsDateAndEndsAtTheEndOfItsWindow() {
            settings = new PhaseSettings(2 * HOUR, at, 0L, HOUR, List.of(), UTC, PhaseSettings.PurgeRules.NONE);
            assertTrue(phases.tick().isEmpty());

            now.set(at);
            assertEquals(List.of(Type.SOTW_STARTED), types(phases.tick()));
            assertEquals(2 * HOUR, phases.getSotwRemainingSeconds());
        }

        @Test
        void aServerDownAtTheStartStillGetsWhatIsLeftOfTheWindow() {
            settings = new PhaseSettings(2 * HOUR, at, 0L, HOUR, List.of(), UTC, PhaseSettings.PurgeRules.NONE);
            now.set(at + 30 * 60 * SECOND);

            assertEquals(List.of(Type.SOTW_STARTED), types(phases.tick()));
            assertEquals(90 * 60L, phases.getSotwRemainingSeconds(), "ends when it would have");
        }

        @Test
        void aDateLeftFromAnotherMapNeverFires() {
            settings = new PhaseSettings(2 * HOUR, at, at, HOUR, List.of(), UTC, PhaseSettings.PurgeRules.NONE);
            now.set(at + 3 * HOUR * SECOND);

            assertTrue(phases.tick().isEmpty());
            assertFalse(phases.isSotw());
            assertFalse(phases.isEotw(), "a new map must not open straight into EOTW");
        }

        @Test
        void aScheduledSotwStoppedByStaffDoesNotComeBack() {
            settings = new PhaseSettings(2 * HOUR, at, 0L, HOUR, List.of(), UTC, PhaseSettings.PurgeRules.NONE);
            now.set(at);
            phases.tick();
            phases.stopSotw();

            advance(60);
            assertTrue(phases.tick().isEmpty());
            assertFalse(phases.isSotw());
        }

        @Test
        void aSotwAlreadyRunningStandsInForTheScheduledOne() {
            settings = new PhaseSettings(2 * HOUR, at, 0L, HOUR, List.of(), UTC, PhaseSettings.PurgeRules.NONE);
            phases.startSotw(15 * 60L); // staff started one that ends five minutes after the date
            now.set(at);
            assertTrue(phases.tick().isEmpty());

            advance(5 * 60);
            assertEquals(List.of(Type.SOTW_ENDED), types(phases.tick()));
            advance(60);
            assertTrue(phases.tick().isEmpty(), "the scheduled one does not start in its place afterwards");
        }

        @Test
        void aScheduledEotwWaitsForTheEndOfASotwWithinItsWindow() {
            settings = new PhaseSettings(2 * HOUR, 0L, at, HOUR, List.of(), UTC, PhaseSettings.PurgeRules.NONE);
            phases.startSotw(20 * 60L);

            now.set(at);
            assertTrue(phases.tick().isEmpty());
            assertFalse(phases.isEotw());

            advance(10 * 60);
            assertEquals(List.of(Type.SOTW_ENDED, Type.EOTW_STARTED), types(phases.tick()),
                    "in the order they happened");
            assertTrue(phases.isEotw());
        }

        @Test
        void aScheduledEotwStoppedByStaffDoesNotComeBack() {
            settings = new PhaseSettings(2 * HOUR, 0L, at, HOUR, List.of(), UTC, PhaseSettings.PurgeRules.NONE);
            now.set(at);
            phases.tick();
            phases.stopEotw();

            advance(60);
            assertTrue(phases.tick().isEmpty());
            assertFalse(phases.isEotw());
        }
    }

    /** Instants, not ticks: a restart changes neither phase, nor who chose to fight. */
    @Nested
    class Restarts {

        @Test
        void aSotwSurvivesARestartWithItsChoices() throws Exception {
            phases.startSotw(2 * HOUR);
            phases.enablePvp(alice);
            assertTrue(phases.flush());

            advance(HOUR + 30 * 60);
            PhaseManager restarted = new PhaseManager(() -> settings, store, now::get);
            restarted.loadAll();

            assertTrue(restarted.isSotw());
            assertEquals(30 * 60L, restarted.getSotwRemainingSeconds());
            assertTrue(restarted.hasEnabledPvp(alice));
            assertEquals(List.of(), types(restarted.tick()),
                    "the 1h mark it slept through is not announced late");
        }

        @Test
        void aSotwThatEndedWhileTheServerWasDownIsNotAnnouncedAndForgetsItsChoices() throws Exception {
            phases.startSotw(HOUR);
            phases.enablePvp(alice);
            phases.flush();

            advance(2 * HOUR);
            PhaseManager restarted = new PhaseManager(() -> settings, store, now::get);
            restarted.loadAll();

            assertFalse(restarted.isSotw());
            assertTrue(restarted.tick().isEmpty(), "nobody was online to hear it begin or end");
            assertTrue(restarted.flush(), "the stale choices are cleared from storage");
            assertEquals(Set.of(), store.enabled);
        }

        @Test
        void eotwSurvivesARestart() throws Exception {
            phases.startEotw();
            phases.flush();

            PhaseManager restarted = new PhaseManager(() -> settings, store, now::get);
            restarted.loadAll();
            assertTrue(restarted.isEotw());
        }

        @Test
        void aFailedWriteStaysPending() throws Exception {
            phases.startEotw();
            store.failNextSave = true;
            assertThrows(IllegalStateException.class, phases::flush);

            assertTrue(phases.flush(), "retried at the next flush");
            assertTrue(store.state.eotwSince() > 0);
        }

        @Test
        void nothingChangedMeansNothingWritten() throws Exception {
            assertFalse(phases.flush());
        }
    }

    @Test
    void settingsRejectValuesThatCannotWork() {
        assertThrows(IllegalArgumentException.class, () -> new PhaseSettings(0L, 0L, 0L, HOUR, List.of(), UTC, PhaseSettings.PurgeRules.NONE));
        assertThrows(IllegalArgumentException.class, () -> new PhaseSettings(HOUR, 0L, 0L, 0L, List.of(), UTC, PhaseSettings.PurgeRules.NONE));
    }

    // ------------------------------------------------------------------

    /** The Purge, decided 12/09/2026: every team raidable for a while, and nothing else of EOTW. */
    @Nested
    class Purge {

        private final UUID team = UUID.randomUUID();

        @Test
        void everyTeamIsRaidableWhileItRunsAndOnlyThen() {
            RaidabilityPolicy raids = phases.raidability(RaidabilityPolicy.NEVER);
            assertFalse(raids.isRaidable(team));

            assertTrue(phases.startPurge(30 * 60).success());
            assertTrue(raids.isRaidable(team));
            assertEquals(Rule.USUAL, phases.deathbanRule(), "deathbans as usual - it is not EOTW");
            assertTrue(phases.claimingRefusal().isEmpty(), "and claiming stays open");

            advance(30 * 60);
            assertFalse(raids.isRaidable(team));
            assertEquals(List.of(Type.PURGE_ENDED), types(phases.tick()));
            assertTrue(phases.tick().isEmpty(), "its end is announced once");
        }

        @Test
        void neverDuringSotwNorEotw() {
            phases.startSotw(HOUR);
            assertEquals(PhaseMessages.PURGE_DURING_SOTW, phases.startPurge(60).messageKey());
            phases.stopSotw();
            phases.startEotw();
            assertEquals(PhaseMessages.PURGE_DURING_EOTW, phases.startPurge(60).messageKey());
        }

        /** SOTW protects everybody: claims raidable under a map where nobody can fight make no sense. */
        @Test
        void aSotwThatBeginsEndsARunningPurge() {
            phases.startPurge(HOUR);
            phases.startSotw(HOUR);
            assertFalse(phases.isPurge());
            assertFalse(types(phases.tick()).contains(Type.PURGE_ENDED), "SOTW's own start is what is heard");
        }

        @Test
        void staffCanStopItAndItIsAnnouncedOnce() {
            phases.startPurge(HOUR);
            PhaseResult stopped = phases.stopPurge();
            assertEquals(Type.PURGE_STOPPED, stopped.broadcast().orElseThrow().type());
            assertTrue(phases.tick().isEmpty());
            assertEquals(PhaseMessages.PURGE_NOT_ACTIVE, phases.stopPurge().messageKey());
        }

        @Test
        void aSecondStartIsRefused() {
            phases.startPurge(HOUR);
            assertEquals(PhaseMessages.PURGE_ALREADY_ACTIVE, phases.startPurge(HOUR).messageKey());
        }

        @Test
        void aScheduledPurgeStartsAtItsTimeOnce() {
            java.time.ZonedDateTime base = java.time.Instant.ofEpochMilli(now.get()).atZone(UTC);
            java.time.LocalTime inAMinute = base.toLocalTime().plusMinutes(1).withSecond(0).withNano(0);
            settings = new PhaseSettings(2 * HOUR, 0L, 0L, HOUR, List.of(), UTC,
                    new PhaseSettings.PurgeRules(600L, List.of(inAMinute)));
            phases = new PhaseManager(() -> settings, store, now::get);

            assertTrue(phases.tick().isEmpty());
            advance(2 * 60);
            assertEquals(List.of(Type.PURGE_STARTED), types(phases.tick()));
            assertEquals(600L, phases.getPurgeRemainingSeconds(), "measured from when it is noticed");
            assertTrue(phases.tick().isEmpty(), "and it does not start twice");
        }

        /** A time that passed while the server was off is not replayed on the way back up. */
        @Test
        void aScheduledTimeMissedWhileDownDoesNotFire() throws Exception {
            java.time.ZonedDateTime base = java.time.Instant.ofEpochMilli(now.get()).atZone(UTC);
            java.time.LocalTime aMinuteAgo = base.toLocalTime().minusMinutes(1).withSecond(0).withNano(0);
            settings = new PhaseSettings(2 * HOUR, 0L, 0L, HOUR, List.of(), UTC,
                    new PhaseSettings.PurgeRules(600L, List.of(aMinuteAgo)));
            phases = new PhaseManager(() -> settings, store, now::get);
            phases.loadAll();
            assertTrue(phases.tick().isEmpty());
            assertFalse(phases.isPurge());
        }

        /** It ends at an instant: a restart in the middle does not cut it short. */
        @Test
        void aPurgeOutlivesARestart() throws Exception {
            phases.startPurge(HOUR);
            phases.flush();
            advance(10 * 60);

            PhaseManager after = new PhaseManager(() -> settings, store, now::get);
            after.loadAll();
            assertTrue(after.isPurge());
            assertEquals(50 * 60L, after.getPurgeRemainingSeconds());
            assertTrue(after.tick().isEmpty(), "it is not announced again after the restart");
        }
    }

    // ------------------------------------------------------------------

    private static final class RecordingStore implements PhaseStore {

        PhaseState state = PhaseState.NONE;
        Set<UUID> enabled = Set.of();
        boolean failNextSave;

        @Override
        public void initSchema() {
        }

        @Override
        public Snapshot load() {
            return new Snapshot(state, enabled);
        }

        @Override
        public void save(PhaseState state, Set<UUID> sotwPvpEnabled) {
            if (failNextSave) {
                failNextSave = false;
                throw new IllegalStateException("database unreachable");
            }
            this.state = state;
            this.enabled = Set.copyOf(sotwPvpEnabled);
        }
    }
}
