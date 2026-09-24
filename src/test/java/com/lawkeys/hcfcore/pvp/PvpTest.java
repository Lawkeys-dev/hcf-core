package com.lawkeys.hcfcore.pvp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rule-level tests for the PvP module: combat tags, deathbans and the combat
 * arithmetic, all without a server.
 */
class PvpTest {

    private static final long SECOND = 1_000L;

    private final AtomicLong now = new AtomicLong(1_000_000L);
    private PvpSettings settings;

    private UUID alice;
    private UUID bob;

    @BeforeEach
    void setUp() {
        settings = PvpSettings.defaults();
        alice = UUID.randomUUID();
        bob = UUID.randomUUID();
    }

    private void advance(long millis) {
        now.addAndGet(millis);
    }

    private void reconfigure(PvpSettings replacement) {
        this.settings = replacement;
    }

    // ------------------------------------------------------------------

    @Nested
    class CombatTags {

        private CombatTagManager tags;

        @BeforeEach
        void createManager() {
            tags = new CombatTagManager(() -> settings, now::get);
        }

        @Test
        void taggingLastsTheConfiguredDuration() {
            assertTrue(tags.tag(alice), "a fresh tag reports itself as new");
            assertTrue(tags.isTagged(alice));
            assertEquals(30L, tags.getRemainingSeconds(alice));

            advance(29 * SECOND);
            assertTrue(tags.isTagged(alice));

            advance(2 * SECOND);
            assertFalse(tags.isTagged(alice));
            assertEquals(0L, tags.getRemainingSeconds(alice));
        }

        @Test
        void reTaggingExtendsWithoutReportingItselfAsNew() {
            tags.tag(alice);
            advance(20 * SECOND);

            assertFalse(tags.tag(alice), "already tagged, so the player must not be told again");
            assertEquals(30L, tags.getRemainingSeconds(alice), "the timer is refreshed");
        }

        @Test
        void aTagCanBeClearedOutright() {
            tags.tag(alice);

            tags.clear(alice);

            assertFalse(tags.isTagged(alice));
        }

        @Test
        void pollReportsEachExpiryExactlyOnce() {
            tags.tag(alice);
            tags.tag(bob);
            assertTrue(tags.pollExpired().isEmpty());

            advance(31 * SECOND);

            assertEquals(Set.of(alice, bob), tags.pollExpired());
            assertTrue(tags.pollExpired().isEmpty(), "an expiry is reported once");
        }

        @Test
        void taggingIsSkippedWhenDisabled() {
            reconfigure(withCombatTag(new PvpSettings.CombatTagRules(false, 30L, true, true, true)));

            assertFalse(tags.tag(alice));
            assertFalse(tags.isTagged(alice));
        }

        @Test
        void aZeroDurationDisablesTaggingToo() {
            reconfigure(withCombatTag(new PvpSettings.CombatTagRules(true, 0L, true, true, true)));

            assertFalse(tags.tag(alice));
            assertFalse(tags.isTagged(alice));
        }

        @Test
        void anUnknownPlayerIsNeverTagged() {
            assertFalse(tags.isTagged(null));
            assertFalse(tags.isTagged(UUID.randomUUID()));
        }

        @Test
        void countingIgnoresExpiredTags() {
            tags.tag(alice);
            tags.tag(bob);
            assertEquals(2, tags.getTaggedCount());

            advance(31 * SECOND);

            assertEquals(0, tags.getTaggedCount());
        }
    }

    @Nested
    class Deathbans {

        private DeathbanManager deathbans;

        @BeforeEach
        void createManager() {
            deathbans = new DeathbanManager(() -> settings, DeathbanStore.NO_OP, now::get);
        }

        @Test
        void aBanKeepsAPlayerOutForItsDuration() {
            Deathban ban = deathbans.apply(alice, 600L, "died").orElseThrow();

            assertTrue(deathbans.isBanned(alice));
            assertEquals(600L, ban.remainingSeconds(now.get()));

            advance(599 * SECOND);
            assertTrue(deathbans.isBanned(alice));

            advance(2 * SECOND);
            assertFalse(deathbans.isBanned(alice));
        }

        @Test
        void aBanKeepsCountingWhileTheServerIsOff() {
            // Stored as an expiry, not a remaining duration - a deathban that paused
            // on restart would be trivially farmable.
            deathbans.apply(alice, 600L, "died");

            advance(10 * 60 * SECOND + SECOND);

            assertFalse(deathbans.isBanned(alice));
        }

        @Test
        void liftingEndsABanEarly() {
            deathbans.apply(alice, 600L, "died");

            assertTrue(deathbans.lift(alice));
            assertFalse(deathbans.isBanned(alice));
            assertFalse(deathbans.lift(alice), "nothing left to lift");
        }

        @Test
        void deathbansCanBeDisabled() {
            reconfigure(withDeathban(new PvpSettings.DeathbanRules(false, 3600L, Map.of())));

            assertTrue(deathbans.apply(alice, 600L, "died").isEmpty());
            assertFalse(deathbans.isBanned(alice));
        }

        @Test
        void aBanUntilTheMapEndsNeverRunsOutAndOnlyStaffLiftIt() {
            Deathban ban = deathbans.applyUntilMapEnd(alice, "eotw").orElseThrow();
            assertTrue(ban.isUntilMapEnd());

            advance(365L * 24 * 3600 * SECOND);
            assertTrue(deathbans.isBanned(alice), "a year on, still out");
            assertEquals(1, deathbans.getActiveBanCount());

            assertTrue(deathbans.lift(alice));
            assertFalse(deathbans.isBanned(alice));
        }

        @Test
        void aBanUntilTheMapEndsStillFollowsTheDeathbanSwitch() {
            reconfigure(withDeathban(new PvpSettings.DeathbanRules(false, 3600L, Map.of())));
            assertTrue(deathbans.applyUntilMapEnd(alice, "eotw").isEmpty());
        }

        @Test
        void anOrdinaryBanIsNotMistakenForOneUntilTheMapEnds() {
            assertFalse(deathbans.apply(alice, 600L, "died").orElseThrow().isUntilMapEnd());
        }

        @Test
        void aNonPositiveDurationBansNobody() {
            assertTrue(deathbans.apply(alice, 0L, "died").isEmpty());
            assertTrue(deathbans.apply(alice, -5L, "died").isEmpty());
        }

        @Test
        void onlyTimedActiveBansCanBeRevived() {
            UUID carol = UUID.randomUUID();
            deathbans.apply(alice, 600L, "died");
            deathbans.apply(bob, 60L, "died");
            deathbans.applyUntilMapEnd(carol, "eotw");

            advance(61 * SECOND);

            assertEquals(List.of(alice), deathbans.getRevivable());
        }

        @Test
        void countingIgnoresExpiredBans() {
            deathbans.apply(alice, 600L, "died");
            deathbans.apply(bob, 60L, "died");
            assertEquals(2, deathbans.getActiveBanCount());

            advance(61 * SECOND);

            assertEquals(1, deathbans.getActiveBanCount());
        }

        @Test
        void flushWritesNewBansAndLifts() throws Exception {
            RecordingStore store = new RecordingStore();
            DeathbanManager persisting = new DeathbanManager(() -> settings, store, now::get);

            persisting.apply(alice, 600L, "died");
            assertEquals(1, persisting.flush());
            assertEquals(List.of(alice), store.saved);

            persisting.lift(alice);
            persisting.flush();
            assertEquals(List.of(alice), store.deleted);
        }

        @Test
        void aFailedWriteKeepsTheBanQueued() {
            RecordingStore store = new RecordingStore();
            store.failOnSave = true;
            DeathbanManager persisting = new DeathbanManager(() -> settings, store, now::get);
            persisting.apply(alice, 600L, "died");

            assertThrows(IllegalStateException.class, persisting::flush);

            store.failOnSave = false;
            try {
                assertEquals(1, persisting.flush(), "the unwritten ban must be retried");
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }

        @Test
        void loadingSkipsBansThatAlreadyExpired() throws Exception {
            RecordingStore store = new RecordingStore();
            store.preloaded.add(new Deathban(alice, now.get() + 600 * SECOND, "died"));
            DeathbanManager loading = new DeathbanManager(() -> settings, store, now::get);

            loading.loadAll();

            assertTrue(loading.isBanned(alice));
            assertTrue(store.purged, "expired rows are cleaned up at startup");
        }
    }

    @Nested
    class DeathbanTiers {

        @Test
        void theShortestMatchingTierWins() {
            // A rank must always be a reduction, never accidentally a penalty, so the
            // shortest match wins rather than the first or the last.
            reconfigure(withDeathban(new PvpSettings.DeathbanRules(true, 3600L,
                    Map.of("rank.a", 1800L, "rank.b", 600L))));

            assertEquals(600L, settings.deathbanSecondsFor(List.of("rank.a", "rank.b")));
            assertEquals(1800L, settings.deathbanSecondsFor(List.of("rank.a")));
        }

        @Test
        void aPlayerWithNoTierGetsTheDefault() {
            assertEquals(3600L, settings.deathbanSecondsFor(List.of()));
            assertEquals(3600L, settings.deathbanSecondsFor(List.of("some.unrelated.node")));
        }
    }

    @Nested
    class StrengthNerf {

        @Test
        void vanillaBonusIsSwappedForTheConfiguredOne() {
            // Defaults: vanilla grants 3.0 per level, this module grants 1.5.
            PvpSettings.StrengthRules rules = settings.strength();

            assertEquals(8.5, CombatMath.nerfStrength(10.0, 1, rules), 1e-9);
            assertEquals(7.0, CombatMath.nerfStrength(10.0, 2, rules), 1e-9);
        }

        @Test
        void anUnbuffedHitIsUntouched() {
            assertEquals(10.0, CombatMath.nerfStrength(10.0, 0, settings.strength()), 1e-9);
        }

        @Test
        void theNerfCanBeDisabled() {
            PvpSettings.StrengthRules off = new PvpSettings.StrengthRules(false, 3.0, 1.5);

            assertEquals(10.0, CombatMath.nerfStrength(10.0, 2, off), 1e-9);
        }

        @Test
        void damageNeverGoesNegative() {
            PvpSettings.StrengthRules harsh = new PvpSettings.StrengthRules(true, 50.0, 0.0);

            assertEquals(0.0, CombatMath.nerfStrength(10.0, 1, harsh), 1e-9);
        }

        @Test
        void bothFiguresAreConfigurableSoAVersionChangeNeedsNoRecompile() {
            // The vanilla bonus is a config value precisely because Mojang has
            // changed it before; a server on a different formula corrects it here.
            PvpSettings.StrengthRules legacy = new PvpSettings.StrengthRules(true, 1.3, 1.0);

            assertEquals(9.7, CombatMath.nerfStrength(10.0, 1, legacy), 1e-9);
        }
    }

    @Nested
    class Knockback {

        @Test
        void tuningScalesHorizontalAndVerticalSeparately() {
            PvpSettings.KnockbackRules rules = new PvpSettings.KnockbackRules(true, 1.5, 0.5);

            assertArrayEquals(new double[] {3.0, 1.0, -6.0},
                    CombatMath.scaleKnockback(new double[] {2.0, 2.0, -4.0}, rules), 1e-9);
        }

        @Test
        void disabledTuningLeavesTheVectorAlone() {
            double[] input = {2.0, 2.0, -4.0};

            assertArrayEquals(input, CombatMath.scaleKnockback(input, settings.knockback()), 1e-9);
        }

        @Test
        void aMalformedVectorIsRejectedRatherThanSilentlyMangled() {
            PvpSettings.KnockbackRules rules = new PvpSettings.KnockbackRules(true, 1.0, 1.0);

            assertThrows(IllegalArgumentException.class,
                    () -> CombatMath.scaleKnockback(new double[] {1.0, 2.0}, rules));
        }
    }

    @Nested
    class AttackSpeed {

        @Test
        void offMeansNoModifierAtAll() {
            assertEquals(0.0, CombatMath.attackSpeedModifier(4.0,
                    new PvpSettings.AttackSpeedRules(false, 1024.0)));
        }

        /** The modifier makes the attribute start from the configured value, whatever the base. */
        @Test
        void theModifierIsTheDifferenceFromTheBase() {
            PvpSettings.AttackSpeedRules rules = new PvpSettings.AttackSpeedRules(true, 16.0);
            assertEquals(12.0, CombatMath.attackSpeedModifier(4.0, rules));
            assertEquals(6.0, CombatMath.attackSpeedModifier(10.0, rules),
                    "another plugin's base is respected, not overwritten");
        }

        @Test
        void aValueEqualToTheBaseNeedsNoModifier() {
            assertEquals(0.0, CombatMath.attackSpeedModifier(4.0,
                    new PvpSettings.AttackSpeedRules(true, 4.0)));
        }

        @Test
        void aLowerValueSlowsAttacksDown() {
            assertEquals(-2.0, CombatMath.attackSpeedModifier(4.0,
                    new PvpSettings.AttackSpeedRules(true, 2.0)));
        }
    }

    // --- settings helpers -------------------------------------------------

    private PvpSettings withDeathban(PvpSettings.DeathbanRules deathban) {
        return new PvpSettings(settings.enabled(), deathban, settings.combatTag(),
                settings.strength(), settings.knockback(), settings.attackSpeed(), settings.safeZones(),
                settings.lootProtection(), settings.friendlyFire(), settings.enderPearl(),
                settings.itemCooldowns());
    }

    private PvpSettings withCombatTag(PvpSettings.CombatTagRules combatTag) {
        return new PvpSettings(settings.enabled(), settings.deathban(), combatTag,
                settings.strength(), settings.knockback(), settings.attackSpeed(), settings.safeZones(),
                settings.lootProtection(), settings.friendlyFire(), settings.enderPearl(),
                settings.itemCooldowns());
    }

    // --- test double ------------------------------------------------------

    private static class RecordingStore implements DeathbanStore {
        final List<Deathban> preloaded = new ArrayList<>();
        final List<UUID> saved = new ArrayList<>();
        final List<UUID> deleted = new ArrayList<>();
        boolean purged;
        boolean failOnSave;

        @Override
        public void initSchema() {
        }

        @Override
        public Collection<Deathban> loadActive(long now) {
            return List.copyOf(preloaded);
        }

        @Override
        public void save(Deathban deathban) {
            if (failOnSave) {
                throw new IllegalStateException("simulated write failure");
            }
            saved.add(deathban.playerId());
        }

        @Override
        public void delete(UUID playerId) {
            deleted.add(playerId);
        }

        @Override
        public int purgeExpired(long now) {
            purged = true;
            return 0;
        }
    }
}
