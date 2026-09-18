package com.lawkeys.hcfcore.stats;

import com.lawkeys.hcfcore.stats.StatsManager.Ranking;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Rule-level tests for player statistics, without a server. */
class StatsTest {

    private final AtomicLong now = new AtomicLong(1_000_000L);
    private StatsManager stats;
    private UUID alice;
    private UUID bob;

    @BeforeEach
    void setUp() {
        stats = new StatsManager(StatsStore.NO_OP, now::get);
        alice = UUID.randomUUID();
        bob = UUID.randomUUID();
    }

    @Nested
    class Persistence {

        /** A kill recorded on the main thread while the async flush writes the same row. */
        @Test
        void aKillRecordedDuringTheWriteIsWrittenAtTheNextFlush() throws Exception {
            java.util.Map<UUID, Integer> writtenKills = new java.util.HashMap<>();
            Runnable[] duringSave = {() -> { }};
            StatsManager stored = new StatsManager(new StatsStore() {
                @Override
                public void initSchema() {
                }

                @Override
                public java.util.Collection<PlayerStats> loadAll() {
                    return java.util.List.of();
                }

                @Override
                public void save(PlayerStats row) {
                    int kills = row.getKills();
                    Runnable hook = duringSave[0];
                    duringSave[0] = () -> { };
                    hook.run();
                    writtenKills.put(row.getPlayerId(), kills);
                }
            }, now::get);
            stored.recordKill(alice, "Alice");
            duringSave[0] = () -> stored.recordKill(alice, "Alice");
            stored.flush();

            stored.flush();
            assertEquals(2, writtenKills.get(alice), "the second kill must not be left unwritten");
        }
    }

    @Nested
    class KillsAndDeaths {

        @Test
        void aKillCountsAndStartsAStreak() {
            assertEquals(1, stats.recordKill(alice, "Alice"));
            assertEquals(2, stats.recordKill(alice, "Alice"));

            PlayerStats row = stats.find(alice).orElseThrow();
            assertEquals(2, row.getKills());
            assertEquals(2, row.getKillstreak());
            assertEquals(2, row.getHighestKillstreak());
        }

        @Test
        void aDeathEndsTheStreakButNotTheRecord() {
            stats.recordKill(alice, "Alice");
            stats.recordKill(alice, "Alice");
            stats.recordKill(alice, "Alice");
            stats.recordDeath(alice, "Alice");

            PlayerStats row = stats.find(alice).orElseThrow();
            assertEquals(0, row.getKillstreak(), "the streak is over");
            assertEquals(3, row.getHighestKillstreak(), "but it happened");
            assertEquals(3, row.getKills());
            assertEquals(1, row.getDeaths());
        }

        @Test
        void aNewStreakOnlyBeatsTheRecordWhenItPassesIt() {
            for (int i = 0; i < 5; i++) {
                stats.recordKill(alice, "Alice");
            }
            stats.recordDeath(alice, "Alice");
            stats.recordKill(alice, "Alice");

            assertEquals(5, stats.find(alice).orElseThrow().getHighestKillstreak());
        }

        /**
         * A player with no deaths would otherwise be infinity, which no leaderboard
         * can sort and no scoreboard can render.
         */
        @Test
        void aDeathFreePlayerRatesAsTheirKillCount() {
            stats.recordKill(alice, "Alice");
            stats.recordKill(alice, "Alice");
            assertEquals(2.0, stats.find(alice).orElseThrow().killDeathRatio(), 1e-9);

            stats.recordDeath(alice, "Alice");
            assertEquals(2.0, stats.find(alice).orElseThrow().killDeathRatio(), 1e-9);
        }

        @Test
        void playersAreIndependent() {
            stats.recordKill(alice, "Alice");
            stats.recordDeath(bob, "Bob");
            assertEquals(1, stats.find(alice).orElseThrow().getKills());
            assertEquals(0, stats.find(bob).orElseThrow().getKills());
            assertEquals(1, stats.find(bob).orElseThrow().getDeaths());
        }

        @Test
        void aPlayerNeverSeenHasNoRow() {
            assertTrue(stats.find(alice).isEmpty());
            assertEquals(0, stats.size());
        }
    }

    @Nested
    class Playtime {

        @Test
        void aSessionInProgressCountsTowardsTheTotal() {
            stats.beginSession(alice, "Alice");
            now.addAndGet(60_000L);

            assertEquals(60L, stats.find(alice).orElseThrow().playtimeSeconds(now.get()));
        }

        @Test
        void endingASessionBanksIt() {
            stats.beginSession(alice, "Alice");
            now.addAndGet(60_000L);
            stats.endSession(alice);

            PlayerStats row = stats.find(alice).orElseThrow();
            assertEquals(60L, row.storedPlaytimeSeconds());
            // And time passing while offline adds nothing.
            now.addAndGet(3_600_000L);
            assertEquals(60L, row.playtimeSeconds(now.get()));
        }

        @Test
        void sessionsAccumulate() {
            stats.beginSession(alice, "Alice");
            now.addAndGet(30_000L);
            stats.endSession(alice);

            now.addAndGet(1_000_000L);

            stats.beginSession(alice, "Alice");
            now.addAndGet(45_000L);
            stats.endSession(alice);

            assertEquals(75L, stats.find(alice).orElseThrow().storedPlaytimeSeconds());
        }

        /**
         * The one that would have quietly lost time: playtime is only banked when a
         * session ends, so a shutdown has to end everybody's before the final flush.
         */
        @Test
        void shutdownBanksEverybodySessionSoFar() {
            stats.beginSession(alice, "Alice");
            stats.beginSession(bob, "Bob");
            now.addAndGet(120_000L);

            stats.endAllSessions();

            assertEquals(120L, stats.find(alice).orElseThrow().storedPlaytimeSeconds());
            assertEquals(120L, stats.find(bob).orElseThrow().storedPlaytimeSeconds());
        }

        @Test
        void firstSeenIsTheFirstSessionAndDoesNotMove() {
            long first = now.get();
            stats.beginSession(alice, "Alice");
            stats.endSession(alice);

            now.addAndGet(5_000_000L);
            stats.beginSession(alice, "Alice");

            PlayerStats row = stats.find(alice).orElseThrow();
            assertEquals(first, row.getFirstSeen());
            assertEquals(now.get(), row.getLastSeen());
        }

        @Test
        void endingASessionThatNeverBeganChangesNothing() {
            stats.recordKill(alice, "Alice");
            stats.endSession(alice);
            assertEquals(0L, stats.find(alice).orElseThrow().storedPlaytimeSeconds());
        }
    }

    @Nested
    class Leaderboards {

        @BeforeEach
        void populate() {
            for (int i = 0; i < 5; i++) {
                stats.recordKill(alice, "Alice");
            }
            stats.recordDeath(alice, "Alice");
            for (int i = 0; i < 3; i++) {
                stats.recordKill(bob, "Bob");
            }
        }

        @Test
        void topByKillsIsBestFirst() {
            List<PlayerStats> top = stats.top(Ranking.KILLS, 10);
            assertEquals(List.of("Alice", "Bob"), top.stream().map(PlayerStats::getName).toList());
        }

        @Test
        void theLimitIsRespected() {
            assertEquals(1, stats.top(Ranking.KILLS, 1).size());
            assertEquals(0, stats.top(Ranking.KILLS, 0).size());
        }

        @Test
        void askingForMoreThanExistsIsFine() {
            assertEquals(2, stats.top(Ranking.KILLS, 100).size());
        }

        /**
         * Alice is 5/1 = 5.0; Bob is 3 kills and no deaths, which rates as 3.0 rather
         * than as infinity. So the player who has died ranks above the one who has
         * not - which is the point of rating a death-free player at their kill count.
         */
        @Test
        void aDeathFreePlayerDoesNotAutomaticallyTopTheRatio() {
            assertEquals("Alice", stats.top(Ranking.KILL_DEATH_RATIO, 1).get(0).getName());
        }

        /** Equal scores must come back in a stable order, not the map's whim. */
        @Test
        void tiesBreakByName() {
            UUID carol = UUID.randomUUID();
            UUID dave = UUID.randomUUID();
            stats.recordKill(dave, "Dave");
            stats.recordKill(carol, "Carol");

            List<String> names = stats.top(Ranking.KILLS, 10).stream()
                    .map(PlayerStats::getName).toList();
            assertTrue(names.indexOf("Carol") < names.indexOf("Dave"));
        }
    }

    @Nested
    class Lookup {

        @Test
        void aPlayerIsFoundByNameWhateverTheCase() {
            stats.recordKill(alice, "Alice");
            assertTrue(stats.findByName("alice").isPresent());
            assertTrue(stats.findByName("ALICE").isPresent());
            assertTrue(stats.findByName("Nobody").isEmpty());
            assertTrue(stats.findByName("  ").isEmpty());
        }

        /** A rename must follow the player, or leaderboards show the old name for ever. */
        @Test
        void theNameFollowsTheUuid() {
            stats.recordKill(alice, "Alice");
            stats.recordKill(alice, "AliceRenamed");

            assertEquals("AliceRenamed", stats.find(alice).orElseThrow().getName());
            assertTrue(stats.findByName("Alice").isEmpty());
            assertTrue(stats.findByName("AliceRenamed").isPresent());
        }

        @Test
        void gettingAPlayerCreatesTheirRow() {
            assertFalse(stats.find(alice).isPresent());
            stats.get(alice, "Alice");
            assertTrue(stats.find(alice).isPresent());
            assertEquals(0, stats.find(alice).orElseThrow().getKills());
        }
    }
}
