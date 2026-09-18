package com.lawkeys.hcfcore.util;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Refusal messages: a held mouse button repeats the click five times a second, and
 * the chat must not repeat with it.
 */
class RefusalThrottleTest {

    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();

    @Test
    void theFirstClickIsAlwaysExplained() {
        assertTrue(new RefusalThrottle(2000).tryTell(ALICE, 0));
    }

    /** Five repeats a second, as a held button sends them: one message. */
    @Test
    void aHeldButtonIsExplainedOnce() {
        RefusalThrottle throttle = new RefusalThrottle(2000);
        assertTrue(throttle.tryTell(ALICE, 0));
        for (long now = 200; now < 2000; now += 200) {
            assertFalse(throttle.tryTell(ALICE, now), "repeat at " + now + " ms");
        }
    }

    @Test
    void theNextMessageComesOnceTheIntervalHasPassed() {
        RefusalThrottle throttle = new RefusalThrottle(2000);
        throttle.tryTell(ALICE, 0);
        assertTrue(throttle.tryTell(ALICE, 2000));
        assertFalse(throttle.tryTell(ALICE, 3999), "the interval restarts from the last message");
    }

    @Test
    void playersAreThrottledSeparately() {
        RefusalThrottle throttle = new RefusalThrottle(2000);
        throttle.tryTell(ALICE, 0);
        assertTrue(throttle.tryTell(BOB, 100));
    }

    /** A player who leaves and comes back is not still muted from before. */
    @Test
    void aForgottenPlayerIsExplainedAgainAtOnce() {
        RefusalThrottle throttle = new RefusalThrottle(2000);
        throttle.tryTell(ALICE, 0);
        throttle.forget(ALICE);
        assertTrue(throttle.tryTell(ALICE, 100));
    }

    @Test
    void aNegativeIntervalIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new RefusalThrottle(-1));
    }
}
