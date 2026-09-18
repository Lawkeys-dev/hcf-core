package com.lawkeys.hcfcore.chat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Local chat's copy of where everybody is, read from the chat thread. */
class PositionsTest {

    private final UUID overworld = UUID.randomUUID();
    private final UUID nether = UUID.randomUUID();
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final Positions positions = new Positions();

    @BeforeEach
    void setUp() {
        positions.replace(Map.of(
                alice, new Positions.Position(overworld, 0, 64, 0),
                bob, new Positions.Position(overworld, 30, 64, 40)));
    }

    @Test
    void theBoundaryIsInRange() {
        assertTrue(positions.within(alice, bob, 50), "exactly 50 blocks apart");
        assertFalse(positions.within(alice, bob, 49));
    }

    @Test
    void anotherWorldIsNeverInRange() {
        positions.replace(Map.of(
                alice, new Positions.Position(overworld, 0, 64, 0),
                bob, new Positions.Position(nether, 0, 64, 0)));
        assertFalse(positions.within(alice, bob, 1_000));
    }

    /** Somebody who joined since the last copy hears the line rather than losing it. */
    @Test
    void aPlayerNotYetCopiedCountsAsInRange() {
        assertTrue(positions.within(alice, UUID.randomUUID(), 1));
    }

    @Test
    void clearingForgetsEverybody() {
        positions.clear();
        assertTrue(positions.within(alice, bob, 1), "unknown on both sides: in range");
    }
}
