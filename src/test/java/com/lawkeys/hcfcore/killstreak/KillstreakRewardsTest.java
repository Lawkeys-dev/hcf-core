package com.lawkeys.hcfcore.killstreak;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The killstreak reward table, which is all of the module's logic. */
class KillstreakRewardsTest {

    private static KillstreakReward reward(int streak) {
        return new KillstreakReward(streak, "&c%player% is on %streak%!", List.of("give %player% diamond 1"));
    }

    @Test
    void aRewardFiresAtItsOwnStreak() {
        KillstreakRewards rewards = KillstreakRewards.of(List.of(reward(5)), m -> { });
        assertTrue(rewards.at(5).isPresent());
        assertTrue(rewards.at(4).isEmpty());
        assertTrue(rewards.at(6).isEmpty());
    }

    /**
     * Exactly one reward per kill, not every reward up to the streak. A player
     * reaching 10 was already given 3 and 5 on the way up; handing them out again
     * would multiply every table by itself.
     */
    @Test
    void onlyTheMatchingRewardFires() {
        KillstreakRewards rewards =
                KillstreakRewards.of(List.of(reward(3), reward(5), reward(10)), m -> { });
        assertTrue(rewards.at(10).isPresent());
        assertEquals(10, rewards.at(10).orElseThrow().streak());
        assertTrue(rewards.at(7).isEmpty(), "a streak between two rewards gets neither");
    }

    @Test
    void placeholdersAreFilledIn() {
        KillstreakReward five = reward(5);
        assertEquals(List.of("give Alice diamond 1"), five.commandsFor("Alice"));
        assertEquals("&cAlice is on 5!", five.broadcastFor("Alice"));
    }

    @Test
    void theShippedTableIsEmptyOnPurpose() {
        assertTrue(KillstreakRewards.empty().isEmpty());
        assertTrue(KillstreakRewards.empty().at(5).isEmpty());
    }

    @Test
    void anUnreachableStreakIsDropped() {
        List<String> warnings = new ArrayList<>();
        KillstreakRewards rewards =
                KillstreakRewards.of(List.of(reward(0), reward(-2), reward(3)), warnings::add);
        assertEquals(1, rewards.size());
        assertEquals(2, warnings.size());
    }

    /** A reward that neither says nor does anything is a configuration mistake. */
    @Test
    void aRewardThatDoesNothingIsDropped() {
        List<String> warnings = new ArrayList<>();
        KillstreakRewards rewards = KillstreakRewards.of(
                List.of(new KillstreakReward(5, "", List.of())), warnings::add);
        assertTrue(rewards.isEmpty());
        assertEquals(1, warnings.size());
    }

    @Test
    void twoRewardsForOneStreakKeepTheFirst() {
        List<String> warnings = new ArrayList<>();
        KillstreakRewards rewards = KillstreakRewards.of(List.of(
                new KillstreakReward(5, "first", List.of()),
                new KillstreakReward(5, "second", List.of())), warnings::add);

        assertEquals("first", rewards.at(5).orElseThrow().broadcast());
        assertEquals(1, warnings.size());
    }

    @Test
    void rewardsAreListedLowestFirst() {
        KillstreakRewards rewards =
                KillstreakRewards.of(List.of(reward(10), reward(3), reward(5)), m -> { });
        assertEquals(List.of(3, 5, 10), rewards.all().stream().map(KillstreakReward::streak).toList());
    }

    @Test
    void aSilentRewardIsAllowedIfItStillDoesSomething() {
        KillstreakRewards rewards = KillstreakRewards.of(
                List.of(new KillstreakReward(5, "", List.of("give %player% diamond 1"))), m -> { });
        assertEquals(1, rewards.size());
        assertFalse(rewards.at(5).orElseThrow().hasBroadcast());
    }
}
