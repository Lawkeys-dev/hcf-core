package com.lawkeys.hcfcore.dtr;

import com.lawkeys.hcfcore.claim.ClaimManager;
import com.lawkeys.hcfcore.claim.ClaimSettings;
import com.lawkeys.hcfcore.claim.ClaimStore;
import com.lawkeys.hcfcore.claim.ProtectionResult;
import com.lawkeys.hcfcore.team.Team;
import com.lawkeys.hcfcore.team.TeamEventDispatcher;
import com.lawkeys.hcfcore.team.TeamManager;
import com.lawkeys.hcfcore.team.TeamResult;
import com.lawkeys.hcfcore.team.TeamSettings;
import com.lawkeys.hcfcore.team.TeamStore;
import com.lawkeys.hcfcore.util.ChunkPosition;
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

/**
 * Rule-level tests for DTR.
 *
 * <p>Because DTR is derived from time rather than mutated by a task, every
 * "wait 45 minutes" case here is a clock move, and the suite still runs in
 * milliseconds. The {@link ReclaimLoop} group closes the loop that
 * {@code ClaimManagerTest} could only half-verify: it wires the real
 * {@code DtrManager} into the real {@code ClaimManager} and checks that
 * territory protection actually follows DTR end to end.
 */
class DtrManagerTest {

    private static final long SECOND = 1_000L;
    private static final long MINUTE = 60 * SECOND;

    private final AtomicLong now = new AtomicLong(1_000_000L);

    private DtrSettings settings;
    private TeamManager teamManager;
    private DtrManager dtr;

    private UUID alice;
    private Team wizards;

    @BeforeEach
    void setUp() {
        settings = DtrSettings.defaults();
        teamManager = new TeamManager(TeamSettings::defaults, TeamStore.NO_OP,
                TeamEventDispatcher.NO_OP, now::get);
        dtr = new DtrManager(() -> settings, teamManager, DtrStore.NO_OP, now::get);

        alice = UUID.randomUUID();
        wizards = teamManager.createTeam(alice, "Wizards").getTeam().orElseThrow();
    }

    /** Advances the clock, which is the only way time passes in these tests. */
    private void advance(long millis) {
        now.addAndGet(millis);
    }

    /**
     * Kills members until the team actually runs out of DTR.
     *
     * <p>Worth a helper rather than a bare {@code applyDeath}: with the default
     * scale a solo team holds 1.1 DTR and a death costs 1.0, so a single death
     * leaves it at 0.1 and still protected. Two are needed - see
     * {@link Deaths#aSoloTeamSurvivesItsFirstDeath()}.
     */
    private void makeRaidable(Team team) {
        for (int i = 0; i < 20 && !dtr.isRaidable(team.getId()); i++) {
            dtr.applyDeath(team);
        }
        assertTrue(dtr.isRaidable(team.getId()), "setup failed to make the team raidable");
    }

    // ------------------------------------------------------------------

    @Nested
    class Maximum {

        @Test
        void theCeilingScalesWithMemberCountAndIsCapped() {
            // Defaults: 1.1 per member, capped at 6.6.
            assertEquals(1.1, dtr.getMaximum(wizards), 1e-9);

            for (int i = 0; i < 5; i++) {
                teamManager.join(UUID.randomUUID(), wizards, true);
            }
            assertEquals(6.6, dtr.getMaximum(wizards), 1e-9);

            teamManager.join(UUID.randomUUID(), wizards, true);
            assertEquals(6.6, dtr.getMaximum(wizards), 1e-9, "the cap holds past six members");
        }

        @Test
        void aTeamThatNeverDiedSitsAtItsMaximumWithoutAStoredRow() {
            assertEquals(dtr.getMaximum(wizards), dtr.getDtr(wizards), 1e-9);
            assertTrue(dtr.getStates().isEmpty(), "no row is written until something happens");
        }

        @Test
        void losingMembersLowersTheCeilingAndClampsTheCurrentValue() {
            UUID bob = UUID.randomUUID();
            teamManager.join(bob, wizards, true);
            assertEquals(2.2, dtr.getDtr(wizards), 1e-9);

            teamManager.kick(wizards, null, bob);

            assertEquals(1.1, dtr.getMaximum(wizards), 1e-9);
            assertEquals(1.1, dtr.getDtr(wizards), 1e-9, "DTR cannot sit above the new ceiling");
        }
    }

    @Nested
    class Deaths {

        @Test
        void aDeathCostsDtrAndRegenerationGoesOn() {
            teamManager.join(UUID.randomUUID(), wizards, true);
            teamManager.join(UUID.randomUUID(), wizards, true);
            assertEquals(3.3, dtr.getDtr(wizards), 1e-9);

            assertEquals(2.3, dtr.applyDeath(wizards).orElseThrow(), 1e-9);
            assertFalse(dtr.isFrozen(wizards), "no pause after a death any more");
            assertEquals(0, dtr.getFreezeRemainingSeconds(wizards));
        }

        @Test
        void dtrStopsFallingAtTheConfiguredFloor() {
            reconfigure(withMinimum(-2.0));

            for (int i = 0; i < 20; i++) {
                dtr.applyDeath(wizards);
            }

            assertEquals(-2.0, dtr.getDtr(wizards), 1e-9);
        }

        @Test
        void aSoloTeamSurvivesItsFirstDeath() {
            // 1.1 DTR against a 1.0 cost: the first death hurts but does not open
            // the base. This is the long-standing HCF scale, not an accident.
            assertEquals(0.1, dtr.applyDeath(wizards).orElseThrow(), 1e-9);
            assertFalse(dtr.isRaidable(wizards.getId()));

            assertEquals(-0.9, dtr.applyDeath(wizards).orElseThrow(), 1e-9);
            assertTrue(dtr.isRaidable(wizards.getId()), "the second death is the one that opens it");
        }

        @Test
        void aDeathWhileAlreadyRaidableDigsTheHoleDeeper() {
            makeRaidable(wizards);
            assertEquals(-0.9, dtr.getDtr(wizards), 1e-9);

            assertEquals(-1.9, dtr.applyDeath(wizards).orElseThrow(), 1e-9);
        }

        @Test
        void systemTeamsHaveNoDtr() {
            Team spawn = teamManager.createSystemTeam("Spawn").getTeam().orElseThrow();

            assertTrue(dtr.applyDeath(spawn).isEmpty());
            assertFalse(dtr.isRaidable(spawn.getId()));
        }

        @Test
        void theModuleCanBeDisabledEntirely() {
            reconfigure(withEnabled(false));

            assertTrue(dtr.applyDeath(wizards).isEmpty());
            assertFalse(dtr.isRaidable(wizards.getId()));
        }
    }

    @Nested
    class Regeneration {

        @Test
        void regenerationStartsAtTheDeathInSteps() {
            dtr.applyDeath(wizards);
            double afterDeath = dtr.getDtr(wizards);

            // Defaults: +0.1 every 90 seconds, from the moment of the death.
            advance(90 * SECOND);
            assertEquals(afterDeath + 0.1, dtr.getDtr(wizards), 1e-9);

            advance(90 * SECOND);
            assertEquals(afterDeath + 0.2, dtr.getDtr(wizards), 1e-9);
        }

        @Test
        void aTeamAtMinusOneIsRaidableForFifteenMinutes() {
            makeRaidable(wizards);
            dtr.setDtr(wizards, -1.0);
            advance(15 * MINUTE - SECOND);
            assertTrue(dtr.isRaidable(wizards.getId()), "still at -0.10");
            advance(SECOND);
            assertFalse(dtr.isRaidable(wizards.getId()), "back to zero after fifteen minutes");
        }

        @Test
        void regenerationIsStepwiseNotContinuous() {
            dtr.applyDeath(wizards);
            double afterDeath = dtr.getDtr(wizards);

            advance(80 * SECOND);
            assertEquals(afterDeath, dtr.getDtr(wizards), 1e-9,
                    "a partial interval grants nothing; the server advertises whole steps");
        }

        @Test
        void regenerationStopsAtTheMaximum() {
            dtr.applyDeath(wizards);

            advance(1000 * MINUTE);

            assertEquals(dtr.getMaximum(wizards), dtr.getDtr(wizards), 1e-9);
        }

        @Test
        void regenerationKeepsRunningWhileTheServerIsDown() {
            // The point of deriving DTR from time rather than ticking it: a team
            // that logs off raidable can come back protected.
            makeRaidable(wizards);
            assertTrue(dtr.isRaidable(wizards.getId()));

            advance(300 * MINUTE);

            assertFalse(dtr.isRaidable(wizards.getId()));
        }

        @Test
        void regenerationCanBeTurnedOffWithAZeroAmount() {
            reconfigure(withRegeneration(new DtrSettings.RegenerationRules(0.0, 180L)));
            makeRaidable(wizards);

            advance(1000 * MINUTE);

            assertTrue(dtr.isRaidable(wizards.getId()), "DTR never comes back on its own");
        }
    }

    @Nested
    class Raidability {

        @Test
        void raidableExactlyWhileDtrHasRunOut() {
            assertFalse(dtr.isRaidable(wizards.getId()));

            makeRaidable(wizards);

            assertTrue(dtr.getDtr(wizards) <= 0);
            assertTrue(dtr.isRaidable(wizards.getId()));
        }

        @Test
        void protectionReturnsAsSoonAsDtrIsBackToZero() {
            reconfigure(withMaximum(new DtrSettings.MaximumRules(1.0, 0.0, 0.0)));
            dtr.applyDeath(wizards);
            assertEquals(0.0, dtr.getDtr(wizards), 1e-9);
            assertFalse(dtr.isRaidable(wizards.getId()), "zero is not negative: protected");

            dtr.applyDeath(wizards);
            assertEquals(-1.0, dtr.getDtr(wizards), 1e-9);
            assertTrue(dtr.isRaidable(wizards.getId()));

            advance(15 * MINUTE);
            assertEquals(0.0, dtr.getDtr(wizards), 1e-9);
            assertFalse(dtr.isRaidable(wizards.getId()), "ten steps of 0.1 add up to zero, float or not");
        }

        @Test
        void theEtaToProtectionIsTheRegenerationItNeeds() {
            reconfigure(withMaximum(new DtrSettings.MaximumRules(1.0, 0.0, 0.0)));
            dtr.applyDeath(wizards);
            dtr.applyDeath(wizards);

            // At -1.00, ten 0.1 steps back to zero.
            assertEquals(900L, dtr.getSecondsUntilProtected(wizards).orElseThrow());
        }

        @Test
        void theEtaAddsAPauseStaffSet() {
            reconfigure(withMaximum(new DtrSettings.MaximumRules(1.0, 0.0, 0.0)));
            dtr.applyDeath(wizards);
            dtr.applyDeath(wizards);
            dtr.setRegenSeconds(wizards, 600L);
            assertEquals(600L + 900L, dtr.getSecondsUntilProtected(wizards).orElseThrow());
        }

        @Test
        void thereIsNoEtaForATeamThatIsNotRaidable() {
            assertTrue(dtr.getSecondsUntilProtected(wizards).isEmpty());
        }

        @Test
        void thereIsNoEtaWhenRegenerationIsOff() {
            reconfigure(withRegeneration(new DtrSettings.RegenerationRules(0.0, 180L)));
            dtr.applyDeath(wizards);

            assertTrue(dtr.getSecondsUntilProtected(wizards).isEmpty());
        }

        @Test
        void pollReportsOnlyTheFlips() {
            dtr.primeRaidabilityBaseline();
            assertTrue(dtr.pollRaidabilityChanges().isEmpty(), "nothing changed yet");

            makeRaidable(wizards);
            assertEquals(Map.of(wizards, true), dtr.pollRaidabilityChanges());
            assertTrue(dtr.pollRaidabilityChanges().isEmpty(), "a flip is reported once");

            advance(300 * MINUTE);
            assertEquals(Map.of(wizards, false), dtr.pollRaidabilityChanges());
        }

        @Test
        void primingAvoidsAnnouncingTeamsThatWereAlreadyRaidableAtStartup() {
            makeRaidable(wizards);

            dtr.primeRaidabilityBaseline();

            assertTrue(dtr.pollRaidabilityChanges().isEmpty(),
                    "a restart must not re-announce raids that were already under way");
        }
    }

    @Nested
    class StaffOverrides {

        @Test
        void setDtrMovesTheValueWithoutTouchingAPauseStaffSet() {
            teamManager.join(UUID.randomUUID(), wizards, true);
            dtr.applyDeath(wizards);
            dtr.setRegenSeconds(wizards, 600L);
            long pauseBefore = dtr.getFreezeRemainingSeconds(wizards);

            assertTrue(dtr.setDtr(wizards, 2.0).isSuccess());

            assertEquals(2.0, dtr.getDtr(wizards), 1e-9);
            assertEquals(pauseBefore, dtr.getFreezeRemainingSeconds(wizards));
        }

        @Test
        void aDeathDoesNotCutShortAPauseStaffSet() {
            dtr.setRegenSeconds(wizards, 600L);
            dtr.applyDeath(wizards);
            assertTrue(dtr.isFrozen(wizards));
        }

        /** Found in game (13/09/2026): /team setdtr Test 0.3 read back 1.10 at once. */
        @Test
        void aValueSetLongAfterTheFreezeIsNotOvertakenByRegenerationFromBeforeIt() {
            teamManager.join(UUID.randomUUID(), wizards, true);
            dtr.applyDeath(wizards);
            advance(1000 * MINUTE);

            assertTrue(dtr.setDtr(wizards, 0.3).isSuccess());
            assertEquals(0.3, dtr.getDtr(wizards), 1e-9);

            // Then it regenerates from there, one step at a time (+0.1 every 90 seconds).
            advance(90 * SECOND);
            assertEquals(0.4, dtr.getDtr(wizards), 1e-9);
        }

        @Test
        void setDtrRefusesValuesOutsideTheAllowedRange() {
            TeamResult tooHigh = dtr.setDtr(wizards, 99.0);
            TeamResult tooLow = dtr.setDtr(wizards, -99.0);

            assertEquals(DtrMessages.SET_OUT_OF_RANGE, tooHigh.getMessageKey());
            assertEquals(DtrMessages.SET_OUT_OF_RANGE, tooLow.getMessageKey());
            assertEquals(dtr.getMaximum(wizards), dtr.getDtr(wizards), 1e-9);
        }

        @Test
        void setRegenShortensOrClearsAPause() {
            dtr.applyDeath(wizards);
            dtr.setRegenSeconds(wizards, 600L);
            double frozenValue = dtr.getDtr(wizards);
            advance(5 * MINUTE);
            assertEquals(frozenValue, dtr.getDtr(wizards), 1e-9, "paused: nothing comes back");

            assertTrue(dtr.setRegenSeconds(wizards, 0L).isSuccess());

            assertFalse(dtr.isFrozen(wizards));
            assertEquals(frozenValue, dtr.getDtr(wizards), 1e-9, "the value itself is untouched");

            advance(90 * SECOND);
            assertEquals(frozenValue + 0.1, dtr.getDtr(wizards), 1e-9);
        }

        @Test
        void setRegenRejectsANegativeDelay() {
            assertEquals(DtrMessages.SET_REGEN_NEGATIVE,
                    dtr.setRegenSeconds(wizards, -1L).getMessageKey());
        }

        @Test
        void resetPutsATeamBackToFullDtr() {
            makeRaidable(wizards);
            assertTrue(dtr.isRaidable(wizards.getId()));

            dtr.reset(wizards);

            assertEquals(dtr.getMaximum(wizards), dtr.getDtr(wizards), 1e-9);
            assertFalse(dtr.isFrozen(wizards));
        }
    }

    /**
     * The end-to-end reclaim loop of FEATURES.md section 3, with the real
     * {@code ClaimManager} driven by the real {@code DtrManager}.
     */
    @Nested
    class ReclaimLoop {

        private ClaimManager claims;
        private Team warlocks;
        private final ChunkPosition base = new ChunkPosition("world", 0, 0);

        @BeforeEach
        void wireModulesTogether() {
            claims = new ClaimManager(ClaimSettings::defaults, teamManager, ClaimStore.NO_OP, now::get);
            // This single line is what the dtr module does at startup.
            claims.setRaidabilityPolicy(dtr);

            warlocks = teamManager.createTeam(UUID.randomUUID(), "Warlocks").getTeam().orElseThrow();
            assertTrue(claims.claim(wizards, null, base.world(), base.minBlockX(), base.minBlockZ(), base.minBlockX() + 15, base.minBlockZ() + 15).isSuccess());
        }

        @Test
        void territoryOpensOnDeathAndClosesAgainOnItsOwn() {
            assertEquals(ProtectionResult.DENIED_CLAIMED, claims.checkProtection(warlocks, base.world(), base.minBlockX() + 8, base.minBlockZ() + 8));

            makeRaidable(wizards);
            assertEquals(ProtectionResult.ALLOWED_RAID, claims.checkProtection(warlocks, base.world(), base.minBlockX() + 8, base.minBlockZ() + 8),
                    "DTR ran out, so the pillage window is open");

            advance(45 * MINUTE + 300 * MINUTE);
            assertEquals(ProtectionResult.DENIED_CLAIMED, claims.checkProtection(warlocks, base.world(), base.minBlockX() + 8, base.minBlockZ() + 8),
                    "DTR regenerated above zero, so protection came back with nothing re-claimed");
        }

        @Test
        void ownershipSurvivesTheWholeCycleUntouched() {
            makeRaidable(wizards);
            assertEquals(wizards.getId(), claims.getOwnerId(base.world(), base.minBlockX() + 8, base.minBlockZ() + 8).orElseThrow());

            // Even at the deepest point of the raid, the land cannot change hands.
            assertTrue(claims.claim(warlocks, null, base.world(), base.minBlockX(), base.minBlockZ(), base.minBlockX() + 15, base.minBlockZ() + 15).isFailure());

            advance(1000 * MINUTE);
            assertEquals(wizards.getId(), claims.getOwnerId(base.world(), base.minBlockX() + 8, base.minBlockZ() + 8).orElseThrow());
            assertEquals(1, claims.getClaimCount(wizards.getId()));
        }

        @Test
        void theOwningTeamKeepsBuildingThroughoutTheRaid() {
            makeRaidable(wizards);

            assertEquals(ProtectionResult.ALLOWED, claims.checkProtection(wizards, base.world(), base.minBlockX() + 8, base.minBlockZ() + 8));
        }
    }

    @Nested
    class Persistence {

        @Test
        void flushWritesChangedTeamsOnly() throws Exception {
            RecordingStore store = new RecordingStore();
            DtrManager persisting = new DtrManager(() -> settings, teamManager, store, now::get);

            assertEquals(0, persisting.flush(), "a team that never died writes nothing");

            persisting.applyDeath(wizards);
            assertEquals(1, persisting.flush());
            assertEquals(List.of(wizards.getId()), store.saved);

            store.saved.clear();
            assertEquals(0, persisting.flush());
        }

        @Test
        void resettingATeamDropsItsRowRatherThanStoringAStaleValue() throws Exception {
            RecordingStore store = new RecordingStore();
            DtrManager persisting = new DtrManager(() -> settings, teamManager, store, now::get);
            persisting.applyDeath(wizards);
            persisting.flush();

            persisting.reset(wizards);
            persisting.flush();

            assertEquals(List.of(wizards.getId()), store.deleted);
        }

        @Test
        void aFailedWriteKeepsTheTeamQueued() {
            RecordingStore store = new RecordingStore();
            store.failOnSave = true;
            DtrManager persisting = new DtrManager(() -> settings, teamManager, store, now::get);
            persisting.applyDeath(wizards);

            assertThrows(IllegalStateException.class, persisting::flush);

            store.failOnSave = false;
            try {
                assertEquals(1, persisting.flush(), "the unwritten change must be retried");
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }

        @Test
        void loadAllRestoresBothTheValueAndTheFreeze() throws Exception {
            RecordingStore store = new RecordingStore();
            store.preloaded.add(new DtrState(wizards.getId(), -0.5, now.get() + 10 * MINUTE));
            DtrManager loading = new DtrManager(() -> settings, teamManager, store, now::get);

            loading.loadAll();

            assertEquals(-0.5, loading.getDtr(wizards), 1e-9);
            assertTrue(loading.isRaidable(wizards.getId()));
            assertTrue(loading.isFrozen(wizards));
        }
    }

    @Test
    void disbandingATeamDropsItsDtr() {
        dtr.applyDeath(wizards);

        dtr.release(wizards.getId());

        assertTrue(dtr.getStates().isEmpty());
    }

    @Test
    void configChangesApplyWithoutRecreatingTheManager() {
        makeRaidable(wizards);
        assertTrue(dtr.isRaidable(wizards.getId()));

        reconfigure(withEnabled(false));

        assertFalse(dtr.isRaidable(wizards.getId()), "/hcf reload must be enough");
    }

    // --- settings helpers -------------------------------------------------

    private void reconfigure(DtrSettings replacement) {
        this.settings = replacement;
    }

    private DtrSettings withEnabled(boolean enabled) {
        return new DtrSettings(enabled, settings.maximum(), settings.lossPerDeath(),
                settings.minimum(), settings.regeneration(), settings.announcements());
    }

    private DtrSettings withMaximum(DtrSettings.MaximumRules maximum) {
        return new DtrSettings(settings.enabled(), maximum, settings.lossPerDeath(),
                settings.minimum(), settings.regeneration(), settings.announcements());
    }

    private DtrSettings withMinimum(double minimum) {
        return new DtrSettings(settings.enabled(), settings.maximum(), settings.lossPerDeath(),
                minimum, settings.regeneration(), settings.announcements());
    }

    private DtrSettings withRegeneration(DtrSettings.RegenerationRules regeneration) {
        return new DtrSettings(settings.enabled(), settings.maximum(), settings.lossPerDeath(),
                settings.minimum(), regeneration, settings.announcements());
    }

    // --- test double ------------------------------------------------------

    private static class RecordingStore implements DtrStore {
        final List<DtrState> preloaded = new ArrayList<>();
        final List<UUID> saved = new ArrayList<>();
        final List<UUID> deleted = new ArrayList<>();
        boolean failOnSave;

        @Override
        public void initSchema() {
        }

        @Override
        public Collection<DtrState> loadAll() {
            return List.copyOf(preloaded);
        }

        @Override
        public void save(DtrState state) {
            if (failOnSave) {
                throw new IllegalStateException("simulated write failure");
            }
            saved.add(state.teamId());
        }

        @Override
        public void delete(UUID teamId) {
            deleted.add(teamId);
        }
    }
}
