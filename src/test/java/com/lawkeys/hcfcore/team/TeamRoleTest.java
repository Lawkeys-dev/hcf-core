package com.lawkeys.hcfcore.team;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamRoleTest {

    @Test
    void hierarchyIsOrderedByWeight() {
        assertTrue(TeamRole.LEADER.outranks(TeamRole.CO_LEADER));
        assertTrue(TeamRole.CO_LEADER.outranks(TeamRole.MEMBER));
        assertFalse(TeamRole.MEMBER.outranks(TeamRole.MEMBER));
        assertTrue(TeamRole.MEMBER.isAtLeast(TeamRole.MEMBER));
        assertFalse(TeamRole.CO_LEADER.isAtLeast(TeamRole.LEADER));
    }

    @Test
    void promotionAndDemotionStopAtTheEnds() {
        assertEquals(Optional.of(TeamRole.CO_LEADER), TeamRole.MEMBER.promoted());
        assertEquals(Optional.of(TeamRole.LEADER), TeamRole.CO_LEADER.promoted());
        assertEquals(Optional.empty(), TeamRole.LEADER.promoted());

        assertEquals(Optional.of(TeamRole.CO_LEADER), TeamRole.LEADER.demoted());
        assertEquals(Optional.of(TeamRole.MEMBER), TeamRole.CO_LEADER.demoted());
        assertEquals(Optional.empty(), TeamRole.MEMBER.demoted());
    }

    @Test
    void parsesConfigAndDatabaseSpellings() {
        assertEquals(Optional.of(TeamRole.CO_LEADER), TeamRole.fromId("co-leader"));
        assertEquals(Optional.of(TeamRole.CO_LEADER), TeamRole.fromId("CO_LEADER"));
        assertEquals(Optional.of(TeamRole.LEADER), TeamRole.fromId("  leader "));
        assertEquals(Optional.empty(), TeamRole.fromId("emperor"));
        assertEquals(Optional.empty(), TeamRole.fromId(null));
    }
}
