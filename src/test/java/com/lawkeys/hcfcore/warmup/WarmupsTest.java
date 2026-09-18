package com.lawkeys.hcfcore.warmup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The countdowns behind /spawn, /logout, /team hq and /team stuck, without a server. */
class WarmupsTest {

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private Warmups warmups;

    @BeforeEach
    void setUp() {
        warmups = new Warmups();
    }

    @Test
    void aCountdownRunsAndComesDue() {
        assertTrue(warmups.start(alice, "spawn", 5_000L, "world", 10, 64, 20));
        assertTrue(warmups.isWarmingUp(alice));
        assertEquals(5L, warmups.of(alice).orElseThrow().remainingSeconds(0L));

        assertTrue(warmups.pollDue(4_000L).isEmpty(), "not yet");
        assertEquals(1, warmups.pollDue(5_000L).size());
        assertFalse(warmups.isWarmingUp(alice), "a due countdown is taken out");
    }

    /**
     * Stacking would let somebody /spawn while a /logout runs and arrive
     * somewhere in the middle of disconnecting.
     */
    @Test
    void aSecondCountdownIsRefusedWhileOneRuns() {
        assertTrue(warmups.start(alice, "spawn", 5_000L, "world", 0, 64, 0));
        assertFalse(warmups.start(alice, "logout", 9_000L, "world", 0, 64, 0));
        assertEquals("spawn", warmups.of(alice).orElseThrow().kind());
    }

    /** Moving means leaving the block, not turning your head. */
    @Test
    void turningOnTheSpotIsNotMoving() {
        warmups.start(alice, "spawn", 5_000L, "world", 10, 64, 20);
        Warmups.Warmup warmup = warmups.of(alice).orElseThrow();

        assertFalse(warmup.hasMoved("world", 10, 64, 20));
        assertTrue(warmup.hasMoved("world", 11, 64, 20));
        assertTrue(warmup.hasMoved("world", 10, 65, 20));
        assertTrue(warmup.hasMoved("nether", 10, 64, 20));
    }

    @Test
    void cancellingReturnsWhatWasCancelled() {
        warmups.start(alice, "logout", 5_000L, "world", 0, 64, 0);
        assertEquals("logout", warmups.cancel(alice).orElseThrow().kind());
        assertFalse(warmups.isWarmingUp(alice));
        assertTrue(warmups.cancel(alice).isEmpty());
    }

    @Test
    void countdownsAreIndependent() {
        warmups.start(alice, "spawn", 5_000L, "world", 0, 64, 0);
        warmups.start(bob, "logout", 9_000L, "world", 0, 64, 0);

        assertEquals(1, warmups.pollDue(5_000L).size());
        assertTrue(warmups.isWarmingUp(bob));
        assertEquals(1, warmups.size());
    }

    @Test
    void remainingNeverGoesNegative() {
        warmups.start(alice, "spawn", 1_000L, "world", 0, 64, 0);
        assertEquals(0L, warmups.of(alice).orElseThrow().remainingSeconds(9_000L));
    }
}
