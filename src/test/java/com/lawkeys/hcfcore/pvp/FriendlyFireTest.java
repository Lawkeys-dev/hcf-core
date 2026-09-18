package com.lawkeys.hcfcore.pvp;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** The owner's rule of 13/09/2026: teammates never, allies only in an event area. */
class FriendlyFireTest {

    private static final UUID RED = UUID.randomUUID();
    private static final UUID BLUE = UUID.randomUUID();
    private static final BooleanSupplier IN_ZONE = () -> true;
    private static final BooleanSupplier OUTSIDE = () -> false;
    private final PvpSettings.FriendlyFireRules rules = PvpSettings.defaults().friendlyFire();

    @Test
    void teammatesNeverHurtEachOther() {
        assertEquals(FriendlyFire.TEAMMATE, FriendlyFire.judge(RED, RED, false, OUTSIDE, rules));
    }

    /** Not even in a KOTH: an event area opens fights between allies, not within a team. */
    @Test
    void notEvenInAnEventArea() {
        assertEquals(FriendlyFire.TEAMMATE, FriendlyFire.judge(RED, RED, false, IN_ZONE, rules));
    }

    @Test
    void alliesAreRefusedOutsideAnEventArea() {
        assertEquals(FriendlyFire.ALLY, FriendlyFire.judge(RED, BLUE, true, OUTSIDE, rules));
    }

    @Test
    void alliesFightInsideAnEventArea() {
        assertEquals(FriendlyFire.ALLOW, FriendlyFire.judge(RED, BLUE, true, IN_ZONE, rules));
    }

    @Test
    void enemiesAreNotThisRulesBusiness() {
        assertEquals(FriendlyFire.ALLOW, FriendlyFire.judge(RED, BLUE, false, OUTSIDE, rules));
    }

    /** A teamless player is on nobody's side - including another teamless player's. */
    @Test
    void aTeamlessPlayerIsOnNobodysSide() {
        assertEquals(FriendlyFire.ALLOW, FriendlyFire.judge(null, RED, false, OUTSIDE, rules));
        assertEquals(FriendlyFire.ALLOW, FriendlyFire.judge(RED, null, false, OUTSIDE, rules));
        assertEquals(FriendlyFire.ALLOW, FriendlyFire.judge(null, null, false, OUTSIDE, rules));
    }

    @Test
    void theEventAreasAreOnlyLookedUpForAllies() {
        AtomicBoolean asked = new AtomicBoolean();
        BooleanSupplier spy = () -> {
            asked.set(true);
            return false;
        };
        FriendlyFire.judge(RED, RED, false, spy, rules);
        FriendlyFire.judge(RED, BLUE, false, spy, rules);
        assertFalse(asked.get());
    }

    @Test
    void bothRulesAreSettings() {
        var open = new PvpSettings.FriendlyFireRules(true, FriendlyFire.AllyRule.ALWAYS);
        assertEquals(FriendlyFire.ALLOW, FriendlyFire.judge(RED, RED, false, OUTSIDE, open));
        assertEquals(FriendlyFire.ALLOW, FriendlyFire.judge(RED, BLUE, true, OUTSIDE, open));

        var closed = new PvpSettings.FriendlyFireRules(false, FriendlyFire.AllyRule.NEVER);
        assertEquals(FriendlyFire.ALLY, FriendlyFire.judge(RED, BLUE, true, IN_ZONE, closed));
    }
}
