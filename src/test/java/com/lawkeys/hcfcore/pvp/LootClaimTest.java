package com.lawkeys.hcfcore.pvp;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Anticlean: a dead player's loot, for the killer and the killer's team only, for a while. */
class LootClaimTest {

    private static final UUID KILLER = UUID.randomUUID();
    private static final UUID KILLERS_TEAM = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();
    private static final UUID OTHER_TEAM = UUID.randomUUID();
    private static final long UNTIL = 10_000L;

    private final LootClaim shared = new LootClaim(KILLER, KILLERS_TEAM, UNTIL);
    private final LootClaim alone = new LootClaim(KILLER, null, UNTIL);

    @Test
    void theKillerPicksItUp() {
        assertTrue(shared.mayPickUp(KILLER, KILLERS_TEAM, 0L));
        assertTrue(alone.mayPickUp(KILLER, null, 0L));
    }

    @Test
    void soDoesTheKillersTeamWhenItShares() {
        UUID teammate = UUID.randomUUID();
        assertTrue(shared.mayPickUp(teammate, KILLERS_TEAM, 0L));
        assertFalse(alone.mayPickUp(teammate, KILLERS_TEAM, 0L), "not shared: the killer's alone");
    }

    @Test
    void nobodyElseDoes() {
        assertFalse(shared.mayPickUp(OTHER, OTHER_TEAM, 0L));
        assertFalse(shared.mayPickUp(OTHER, null, 0L), "a player with no team is somebody else too");
        assertFalse(shared.mayPickUp(null, null, 0L), "nor a mob, nor a hopper");
    }

    @Test
    void afterwardsItIsAnybodys() {
        assertTrue(shared.mayPickUp(OTHER, OTHER_TEAM, UNTIL), "the last millisecond is the first free one");
        assertTrue(shared.mayPickUp(null, null, UNTIL + 1));
    }

    /** A merge keeps one claim of two: loot under a claim must not dissolve into loot under none. */
    @Test
    void protectedLootMergesOnlyWithLootUnderTheSameClaim() {
        assertTrue(LootClaim.mayMerge(null, null, 0L));
        assertTrue(LootClaim.mayMerge(shared, new LootClaim(KILLER, KILLERS_TEAM, UNTIL + 5), 0L));
        assertFalse(LootClaim.mayMerge(shared, null, 0L));
        assertFalse(LootClaim.mayMerge(null, shared, 0L));
        assertFalse(LootClaim.mayMerge(shared, new LootClaim(OTHER, OTHER_TEAM, UNTIL), 0L));
        assertFalse(LootClaim.mayMerge(shared, alone, 0L), "shared and not shared are two claims");
        assertTrue(LootClaim.mayMerge(shared, null, UNTIL), "expired, it is ordinary loot");
    }
}
