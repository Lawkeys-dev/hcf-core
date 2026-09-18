package com.lawkeys.hcfcore.util;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Per-player waits between two uses. */
class CooldownsTest {

    private final Cooldowns cooldowns = new Cooldowns();
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @Test
    void aUseStartsTheWaitAndASecondUseIsRefusedUntilItEnds() {
        assertTrue(cooldowns.tryUse(alice, "recover", 10, 1_000L));
        assertEquals(10, cooldowns.remaining(alice, "recover", 1_000L));
        assertEquals(1, cooldowns.remaining(alice, "recover", 10_999L), "rounded up: 1 ms left is 1s");

        assertFalse(cooldowns.tryUse(alice, "recover", 10, 5_000L));
        assertEquals(6, cooldowns.remaining(alice, "recover", 5_000L), "a refused use does not extend the wait");

        assertEquals(0, cooldowns.remaining(alice, "recover", 11_000L));
        assertTrue(cooldowns.tryUse(alice, "recover", 10, 11_000L));
    }

    @Test
    void waitsAreKeptPerPlayerAndPerKey() {
        cooldowns.start(alice, "crowbar", 30, 0L);
        assertEquals(0, cooldowns.remaining(bob, "crowbar", 0L));
        assertEquals(0, cooldowns.remaining(alice, "other", 0L));
        assertTrue(cooldowns.isWaiting(alice, "crowbar", 29_999L));
        assertFalse(cooldowns.isWaiting(alice, "crowbar", 30_000L));
    }

    @Test
    void noWaitMeansNothingIsRecorded() {
        assertTrue(cooldowns.tryUse(alice, "free", 0, 0L));
        assertTrue(cooldowns.tryUse(alice, "free", 0, 0L));
        assertFalse(cooldowns.isWaiting(alice, "free", 0L));
    }

    @Test
    void forgettingAPlayerEndsAllTheirWaits() {
        cooldowns.start(alice, "a", 60, 0L);
        cooldowns.start(alice, "b", 60, 0L);
        cooldowns.start(bob, "a", 60, 0L);

        cooldowns.forget(alice);
        assertFalse(cooldowns.isWaiting(alice, "a", 0L));
        assertFalse(cooldowns.isWaiting(alice, "b", 0L));
        assertTrue(cooldowns.isWaiting(bob, "a", 0L));

        cooldowns.clearAll();
        assertFalse(cooldowns.isWaiting(bob, "a", 0L));
    }
}
