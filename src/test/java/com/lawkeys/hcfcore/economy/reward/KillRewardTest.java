package com.lawkeys.hcfcore.economy.reward;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KillRewardTest {

    private final UUID killer = UUID.randomUUID();
    private final UUID victim = UUID.randomUUID();

    @Test
    void aKillPaysTheFlatAmount() {
        KillReward.Payout payout = new KillReward().payout(KillReward.Rules.defaults(), killer, victim, 1000.0, 0L);
        assertEquals(10.0, payout.flat());
        assertEquals(0.0, payout.stolen());
    }

    @Test
    void aShareOfTheVictimsBalanceIsTakenRoundedDownToTheCent() {
        KillReward.Rules rules = new KillReward.Rules(true, 0.0, 10.0, 0L);
        KillReward.Payout payout = new KillReward().payout(rules, killer, victim, 123.456, 0L);
        assertEquals(12.34, payout.stolen(), 1e-9);
        assertEquals(12.34, payout.total(), 1e-9);
    }

    @Test
    void theSameVictimPaysNothingAgainWithinTheCooldown() {
        KillReward reward = new KillReward();
        KillReward.Rules rules = KillReward.Rules.defaults();
        assertEquals(10.0, reward.payout(rules, killer, victim, 0.0, 0L).flat());
        assertTrue(reward.payout(rules, killer, victim, 0.0, 299_000L).isEmpty());
        assertEquals(10.0, reward.payout(rules, killer, victim, 0.0, 300_000L).flat());
        // Another victim is not on cooldown.
        assertEquals(10.0, reward.payout(rules, killer, UUID.randomUUID(), 0.0, 1_000L).flat());
    }

    @Test
    void offOrASuicidePaysNothing() {
        KillReward reward = new KillReward();
        assertTrue(reward.payout(new KillReward.Rules(false, 50.0, 10.0, 0L), killer, victim, 100.0, 0L).isEmpty());
        assertTrue(reward.payout(KillReward.Rules.defaults(), killer, killer, 100.0, 0L).isEmpty());
    }

    @Test
    void valuesAreKeptInRange() {
        KillReward.Rules rules = new KillReward.Rules(true, -5.0, 250.0, -1L);
        assertEquals(0.0, rules.amount());
        assertEquals(100.0, rules.stealPercent());
        assertEquals(0L, rules.sameVictimCooldownSeconds());
    }
}
