package com.lawkeys.hcfcore.team;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The officer rank, and what a team decides for itself in {@code /team settings}. */
class TeamCustomSettingsTest {

    private final AtomicLong now = new AtomicLong(1_000_000L);
    private TeamSettings settings;
    private TeamManager manager;
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID carol = UUID.randomUUID();
    private final UUID dave = UUID.randomUUID();
    private Team team;

    @BeforeEach
    void setUp() {
        settings = TeamSettings.defaults();
        manager = new TeamManager(() -> settings, TeamStore.NO_OP, TeamEventDispatcher.NO_OP, now::get);
        team = manager.createTeam(alice, "Wizards").getTeam().orElseThrow();
        manager.join(bob, team, true);
        manager.join(carol, team, true);
    }

    private void custom(TeamSettings.CustomRules custom, int maxOfficers) {
        TeamSettings s = TeamSettings.defaults();
        settings = new TeamSettings(s.names(), s.maxMembers(), s.maxCoLeaders(), s.inviteExpirySeconds(),
                s.disbandOnLastMemberLeave(), s.roleAfterLeadershipTransfer(), s.requiredRoles(), s.alliances(),
                s.focus(), s.rally(), s.bank(), s.points(), s.koth(), maxOfficers, custom, s.shortcuts());
    }

    @Test
    void anOfficerInvitesAsShippedButDoesNotKick() {
        manager.promote(team, alice, bob);
        assertEquals(TeamRole.OFFICER, team.getRole(bob).orElseThrow());
        assertTrue(manager.invite(team, bob, dave).isSuccess());
        assertEquals(TeamMessages.INSUFFICIENT_ROLE, manager.kick(team, bob, carol).getMessageKey());
    }

    @Test
    void aTeamsOwnRoleReplacesTheServersWhereItMayChoose() {
        assertTrue(manager.setPermission(team, alice, "kick", TeamRole.OFFICER, TeamRole.CO_LEADER).isSuccess());
        manager.promote(team, alice, bob);
        assertTrue(manager.kick(team, bob, carol).isSuccess());

        manager.setPermission(team, alice, "kick", null, TeamRole.CO_LEADER);
        assertEquals(TeamRole.CO_LEADER, manager.requiredRole(team, "kick", TeamRole.CO_LEADER));
    }

    @Test
    void aLockedPermissionFollowsTheServer() {
        assertEquals(TeamMessages.SETTINGS_LOCKED,
                manager.setPermission(team, alice, "disband", TeamRole.MEMBER, TeamRole.LEADER).getMessageKey());
        team.setPermission("disband", TeamRole.MEMBER); // stored before the server locked it
        assertEquals(TeamRole.LEADER, manager.requiredRole(team, "disband", TeamRole.LEADER));
    }

    @Test
    void settingsSwitchedOffLeaveEveryTeamOnTheServersRoles() {
        team.setPermission("kick", TeamRole.MEMBER);
        custom(new TeamSettings.CustomRules(false, true, Set.of(), Map.of()), 0);
        assertEquals(TeamRole.CO_LEADER, manager.requiredRole(team, "kick", TeamRole.CO_LEADER));
    }

    @Test
    void nobodyHandsOutMoreThanTheyHave() {
        // The leader lets co-leaders into the settings.
        manager.setPermission(team, alice, "settings", TeamRole.CO_LEADER, TeamRole.LEADER);
        manager.promote(team, alice, bob);
        manager.promote(team, alice, bob);
        assertEquals(TeamRole.CO_LEADER, team.getRole(bob).orElseThrow());

        assertTrue(manager.setPermission(team, bob, "rally", TeamRole.MEMBER, TeamRole.OFFICER).isSuccess());
        assertEquals(TeamMessages.SETTINGS_ABOVE_YOU,
                manager.setPermission(team, bob, "bank-withdraw", TeamRole.MEMBER, TeamRole.LEADER).getMessageKey(),
                "not a permission the co-leader does not hold");
        assertEquals(TeamMessages.SETTINGS_ABOVE_YOU,
                manager.setPermission(team, bob, "rally", TeamRole.LEADER, TeamRole.OFFICER).getMessageKey(),
                "not above their own rank");
    }

    @Test
    void aPromoterRaisesOnlyBelowThemselves() {
        manager.setPermission(team, alice, "promote", TeamRole.CO_LEADER, TeamRole.LEADER);
        manager.promote(team, alice, bob);
        manager.promote(team, alice, bob);

        assertTrue(manager.promote(team, bob, carol).isSuccess());
        assertEquals(TeamRole.OFFICER, team.getRole(carol).orElseThrow());
        assertEquals(TeamMessages.INSUFFICIENT_ROLE, manager.promote(team, bob, carol).getMessageKey(),
                "a co-leader makes no other co-leader");
    }

    @Test
    void theOfficerLimitIsEnforced() {
        custom(TeamSettings.CustomRules.defaults(), 1);
        assertTrue(manager.promote(team, alice, bob).isSuccess());
        assertEquals(TeamMessages.PROMOTE_OFFICER_LIMIT, manager.promote(team, alice, carol).getMessageKey());
    }

    @Test
    void anOpenTeamTakesAnybody() {
        assertEquals(TeamMessages.JOIN_NO_INVITE, manager.join(dave, team, false).getMessageKey());
        assertTrue(manager.setOpen(team, alice, true).isSuccess());
        assertTrue(manager.join(dave, team, false).isSuccess());
    }

    @Test
    void theServerCanForbidOpenTeams() {
        custom(new TeamSettings.CustomRules(true, false, Set.of(), Map.of()), 0);
        assertEquals(TeamMessages.SETTINGS_OPEN_DISABLED, manager.setOpen(team, alice, true).getMessageKey());
        team.setOpen(true); // stored before the server forbade it
        assertFalse(manager.isOpen(team));
        assertEquals(TeamMessages.JOIN_NO_INVITE, manager.join(dave, team, false).getMessageKey());
    }

    @Test
    void theSettingsSurviveASnapshot() {
        manager.setPermission(team, alice, "kick", TeamRole.OFFICER, TeamRole.CO_LEADER);
        manager.setOpen(team, alice, true);
        Team copy = Team.fromSnapshot(team.toSnapshot());
        assertEquals(TeamRole.OFFICER, copy.getPermission("kick").orElseThrow());
        assertTrue(copy.isOpen());
    }

    @Test
    void invitationsAreListedByTeam() {
        manager.invite(team, alice, dave);
        assertEquals(java.util.List.of(dave), manager.getInvitesOf(team));
    }
}
