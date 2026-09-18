package com.lawkeys.hcfcore.team;

import com.lawkeys.hcfcore.util.WorldPosition;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamTest {

    private static Team team(UUID leader) {
        return new Team(UUID.randomUUID(), "Wizards", TeamType.PLAYER, leader, 1_000L);
    }

    @Test
    void leaderIsAMemberOnCreation() {
        UUID leader = UUID.randomUUID();
        Team team = team(leader);

        assertTrue(team.isMember(leader));
        assertEquals(TeamRole.LEADER, team.getRole(leader).orElseThrow());
        assertEquals(1, team.getMemberCount());
        assertEquals(leader, team.getLeader().orElseThrow());
    }

    @Test
    void systemTeamHasNoLeaderAndNoMembers() {
        Team team = new Team(UUID.randomUUID(), "Spawn", TeamType.SYSTEM, null, 0L);

        assertTrue(team.getLeader().isEmpty());
        assertEquals(0, team.getMemberCount());
        assertTrue(team.getType().isSystem());
    }

    @Test
    void countByRoleReportsEveryRoleIncludingEmptyOnes() {
        Team team = team(UUID.randomUUID());
        team.addMember(UUID.randomUUID(), TeamRole.MEMBER);
        team.addMember(UUID.randomUUID(), TeamRole.MEMBER);

        Map<TeamRole, Integer> counts = team.countByRole();
        assertEquals(1, counts.get(TeamRole.LEADER));
        assertEquals(0, counts.get(TeamRole.CO_LEADER));
        assertEquals(2, counts.get(TeamRole.MEMBER));
    }

    @Test
    void removingTheLeaderClearsTheLeaderReference() {
        UUID leader = UUID.randomUUID();
        Team team = team(leader);

        team.removeMember(leader);

        assertTrue(team.getLeader().isEmpty());
        assertFalse(team.isMember(leader));
    }

    @Test
    void promotingAMemberToLeaderMovesTheLeaderReference() {
        UUID oldLeader = UUID.randomUUID();
        UUID newLeader = UUID.randomUUID();
        Team team = team(oldLeader);
        team.addMember(newLeader, TeamRole.MEMBER);

        team.setRole(oldLeader, TeamRole.CO_LEADER);
        team.setRole(newLeader, TeamRole.LEADER);

        assertEquals(newLeader, team.getLeader().orElseThrow());
    }

    @Test
    void bankAndPointsArithmetic() {
        Team team = team(UUID.randomUUID());

        assertEquals(150.0, team.addBalance(150.0));
        assertEquals(50.0, team.addBalance(-100.0));
        assertEquals(50.0, team.getBalance());

        assertEquals(7L, team.addPoints(7L));
        assertEquals(2L, team.addPoints(-5L));
    }

    @Test
    void mutationsMarkTheTeamDirtyAndFlushClearsIt() {
        Team team = team(UUID.randomUUID());
        team.clearDirty();
        assertFalse(team.isDirty());

        team.addPoints(1L);
        assertTrue(team.isDirty());

        team.clearDirty();
        assertFalse(team.isDirty());
    }

    @Test
    void focusAndAllyRequestsDoNotMarkDirtyBecauseTheyAreNotPersisted() {
        Team team = team(UUID.randomUUID());
        team.clearDirty();

        team.addFocusedPlayer(UUID.randomUUID());
        team.addAllyRequest(UUID.randomUUID());

        assertFalse(team.isDirty(), "runtime-only state must not trigger a database write");
    }

    @Test
    void snapshotRoundTripPreservesPersistedState() {
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID allyId = UUID.randomUUID();
        Team team = team(leader);
        team.addMember(member, TeamRole.CO_LEADER);
        team.addAlly(allyId);
        team.addBalance(1234.5);
        team.addPoints(42L);
        team.incrementKothCaptures();
        team.setRally(WorldPosition.of("world", 1.0, 64.0, -3.0), 9_999L);

        Team restored = Team.fromSnapshot(team.toSnapshot());

        assertEquals(team.getId(), restored.getId());
        assertEquals("Wizards", restored.getName());
        assertEquals(TeamType.PLAYER, restored.getType());
        assertEquals(leader, restored.getLeader().orElseThrow());
        assertEquals(TeamRole.CO_LEADER, restored.getRole(member).orElseThrow());
        assertEquals(Set.of(allyId), restored.getAllies());
        assertEquals(1234.5, restored.getBalance());
        assertEquals(42L, restored.getPoints());
        assertEquals(1, restored.getKothCaptures());
        assertEquals(WorldPosition.of("world", 1.0, 64.0, -3.0), restored.getRally().orElseThrow());
        assertEquals(9_999L, restored.getRallyExpiresAt());
        assertFalse(restored.isDirty(), "a team just read from the store is not pending a write");
    }

    @Test
    void snapshotDropsRuntimeOnlyState() {
        Team team = team(UUID.randomUUID());
        team.addFocusedTeam(UUID.randomUUID());
        team.addAllyRequest(UUID.randomUUID());

        Team restored = Team.fromSnapshot(team.toSnapshot());

        assertTrue(restored.getFocusedTeams().isEmpty());
        assertTrue(restored.getOutgoingAllyRequests().isEmpty());
    }

    @Test
    void memberViewsAreImmutableSnapshots() {
        Team team = team(UUID.randomUUID());
        assertThrows(UnsupportedOperationException.class,
                () -> team.getMemberIds().add(UUID.randomUUID()));
        assertThrows(UnsupportedOperationException.class,
                () -> team.getAllies().add(UUID.randomUUID()));
    }
}
