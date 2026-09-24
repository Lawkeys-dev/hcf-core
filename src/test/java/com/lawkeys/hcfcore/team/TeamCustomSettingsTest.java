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
        custom(rules(false, Set.of(JoinMode.values()), JoinMode.INVITE), 0);
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

    private static TeamSettings.CustomRules rules(boolean enabled, Set<JoinMode> modes, JoinMode defaultMode) {
        TeamSettings.CustomRules d = TeamSettings.CustomRules.defaults();
        return new TeamSettings.CustomRules(enabled, modes, defaultMode, d.locked(), d.icons(),
                d.descriptionLength(), d.discordPattern());
    }

    @Test
    void aTeamIsOnInvitationAsShipped() {
        assertEquals(JoinMode.INVITE, manager.joinMode(team));
        assertEquals(TeamMessages.JOIN_NO_INVITE, manager.join(dave, team, false).getMessageKey());
    }

    @Test
    void anOpenTeamTakesAnybody() {
        assertTrue(manager.setJoinMode(team, alice, JoinMode.OPEN).isSuccess());
        assertTrue(manager.join(dave, team, false).isSuccess());
    }

    @Test
    void aClosedTeamTakesNobodyButStaffPutsThemIn() {
        manager.invite(team, alice, dave);
        assertTrue(manager.setJoinMode(team, alice, JoinMode.CLOSED).isSuccess());
        assertEquals(TeamMessages.TEAM_CLOSED, manager.join(dave, team, false).getMessageKey(),
                "an invitation sent before it closed opens nothing");
        assertEquals(TeamMessages.TEAM_CLOSED, manager.invite(team, alice, UUID.randomUUID()).getMessageKey());
        assertTrue(manager.join(dave, team, true).isSuccess(), "staff still can");
    }

    @Test
    void onlyTheModesTheServerAllowsCount() {
        custom(rules(true, Set.of(JoinMode.INVITE, JoinMode.CLOSED), JoinMode.INVITE), 0);
        assertEquals(TeamMessages.SETTINGS_JOIN_MODE_DISABLED,
                manager.setJoinMode(team, alice, JoinMode.OPEN).getMessageKey());
        team.setJoinMode(JoinMode.OPEN); // chosen before the server forbade it
        assertFalse(manager.isOpen(team));
        assertEquals(TeamMessages.JOIN_NO_INVITE, manager.join(dave, team, false).getMessageKey());
    }

    @Test
    void theServersDefaultJoinModeAppliesUntilTheTeamChooses() {
        custom(rules(true, Set.of(JoinMode.values()), JoinMode.OPEN), 0);
        assertTrue(manager.join(dave, team, false).isSuccess());
    }

    @Test
    void onlyWhoMayChangeTheSettingsWritesTheProfile() {
        assertEquals(TeamMessages.INSUFFICIENT_ROLE,
                manager.setDescription(team, bob, "hello").getMessageKey());
        assertTrue(manager.setDescription(team, alice, "  PvP &cfocused   team {prefix}  ").isSuccess());
        assertEquals("PvP cfocused team prefix", team.getDescription().orElseThrow());
        assertTrue(manager.setDescription(team, alice, "").isSuccess());
        assertTrue(team.getDescription().isEmpty());
    }

    @Test
    void theDiscordLinkMustBeAnInvitation() {
        assertEquals(TeamMessages.SETTINGS_DISCORD_INVALID,
                manager.setDiscord(team, alice, "https://evil.example/x").getMessageKey());
        assertTrue(manager.setDiscord(team, alice, "discord.gg/abc123").isSuccess());
        assertEquals("https://discord.gg/abc123", team.getDiscord().orElseThrow());
        assertTrue(manager.setDiscord(team, alice, " ").isSuccess());
        assertTrue(team.getDiscord().isEmpty());
    }

    @Test
    void theSettingsSurviveASnapshot() {
        manager.setPermission(team, alice, "kick", TeamRole.OFFICER, TeamRole.CO_LEADER);
        manager.setJoinMode(team, alice, JoinMode.CLOSED);
        manager.setDescription(team, alice, "Hello");
        manager.setDiscord(team, alice, "discord.gg/abc123");
        Team copy = Team.fromSnapshot(team.toSnapshot());
        assertEquals(TeamRole.OFFICER, copy.getPermission("kick").orElseThrow());
        assertEquals(JoinMode.CLOSED, copy.getJoinMode().orElseThrow());
        assertEquals("Hello", copy.getDescription().orElseThrow());
        assertEquals("https://discord.gg/abc123", copy.getDiscord().orElseThrow());
    }

    @Test
    void invitationsAreListedByTeam() {
        manager.invite(team, alice, dave);
        assertEquals(java.util.List.of(dave), manager.getInvitesOf(team));
    }
}
