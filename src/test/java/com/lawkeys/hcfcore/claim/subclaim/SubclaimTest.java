package com.lawkeys.hcfcore.claim.subclaim;

import com.lawkeys.hcfcore.team.TeamRole;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubclaimTest {

    private static final String HEADER = "[Subclaim]";

    @Test
    void aSignWithTheHeaderIsASubclaimOfTheNamesBelow() {
        Optional<Subclaim> sign = Subclaim.read(List.of("[subclaim]", "Steve", " ", "Alex "), HEADER);
        assertTrue(sign.isPresent());
        assertEquals(Set.of("steve", "alex"), sign.get().names());
    }

    @Test
    void anyOtherSignIsNot() {
        assertTrue(Subclaim.read(List.of("[Elevator]", "Up", "", ""), HEADER).isEmpty());
        assertTrue(Subclaim.read(List.of(), HEADER).isEmpty());
    }

    @Test
    void aNamedMemberOpensItOthersDoNot() {
        Subclaim sign = new Subclaim(Set.of("steve"));
        assertTrue(sign.allows("Steve", TeamRole.MEMBER, TeamRole.CO_LEADER));
        assertFalse(sign.allows("Alex", TeamRole.MEMBER, TeamRole.CO_LEADER));
    }

    @Test
    void coLeadersAndTheLeaderOpenEveryOneAsShipped() {
        Subclaim sign = new Subclaim(Set.of("steve"));
        assertTrue(sign.allows("Boss", TeamRole.LEADER, TeamRole.CO_LEADER));
        assertTrue(sign.allows("Second", TeamRole.CO_LEADER, TeamRole.CO_LEADER));
        assertFalse(sign.allows("Second", TeamRole.CO_LEADER, TeamRole.LEADER));
        assertFalse(sign.allows("Boss", TeamRole.LEADER, null), "with nobody above the signs, only names count");
    }

    @Test
    void severalSignsOnOneContainerAddUp() {
        List<Subclaim> signs = List.of(new Subclaim(Set.of("steve")), new Subclaim(Set.of("alex")));
        assertTrue(Subclaim.anyAllows(signs, "alex", TeamRole.MEMBER, TeamRole.CO_LEADER));
        assertFalse(Subclaim.anyAllows(signs, "bob", TeamRole.MEMBER, TeamRole.CO_LEADER));
        assertTrue(Subclaim.anyAllows(List.of(), "bob", TeamRole.MEMBER, TeamRole.CO_LEADER),
                "a container with no sign is open to its team");
    }
}
