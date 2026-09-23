package com.lawkeys.hcfcore.team;

import com.lawkeys.hcfcore.util.WorldPosition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rule-level tests for the team module.
 *
 * <p>These run with no server and no database, which is the point of keeping the
 * manager free of the Bukkit API (CONTRIBUTING.md section 3, "Tests").
 */
class TeamManagerTest {

    private final AtomicLong now = new AtomicLong(1_000_000L);
    private TeamSettings settings;
    private TeamManager manager;

    private UUID alice;
    private UUID bob;
    private UUID carol;

    @BeforeEach
    void setUp() {
        settings = TeamSettings.defaults();
        manager = new TeamManager(() -> settings, TeamStore.NO_OP, TeamEventDispatcher.NO_OP, now::get);
        alice = UUID.randomUUID();
        bob = UUID.randomUUID();
        carol = UUID.randomUUID();
    }

    /** Replaces the live settings snapshot, as {@code /hcf reload} does at runtime. */
    private void reconfigure(TeamSettings replacement) {
        this.settings = replacement;
    }

    private Team createTeam(UUID leader, String name) {
        TeamResult result = manager.createTeam(leader, name);
        assertTrue(result.isSuccess(), () -> "expected creation to succeed but got " + result);
        return result.getTeam().orElseThrow();
    }

    // ------------------------------------------------------------------

    @Nested
    class Creation {

        @Test
        void createsATeamAndIndexesItEveryWay() {
            Team team = createTeam(alice, "Wizards");

            assertSame(team, manager.getTeam(team.getId()).orElseThrow());
            assertSame(team, manager.getTeamOf(alice).orElseThrow());
            assertSame(team, manager.getTeamByName("wIzArDs").orElseThrow(), "name lookup is case-insensitive");
            assertEquals(1, manager.getTeamCount());
            assertTrue(team.isDirty(), "a new team must be queued for the next flush");
        }

        @Test
        void rejectsAPlayerWhoAlreadyHasATeam() {
            createTeam(alice, "Wizards");

            TeamResult result = manager.createTeam(alice, "Warlocks");

            assertTrue(result.isFailure());
            assertEquals(TeamMessages.CREATE_ALREADY_IN_TEAM, result.getMessageKey());
        }

        @Test
        void rejectsADuplicateNameRegardlessOfCase() {
            createTeam(alice, "Wizards");

            TeamResult result = manager.createTeam(bob, "WIZARDS");

            assertTrue(result.isFailure());
            assertEquals(TeamMessages.NAME_TAKEN, result.getMessageKey());
        }

        @Test
        void enforcesConfiguredNameRules() {
            reconfigure(withNames(new TeamSettings.NameRules(
                    4, 8, Pattern.compile("^[A-Za-z]+$"), Set.of("staff"))));

            assertEquals(TeamMessages.NAME_TOO_SHORT, manager.createTeam(alice, "abc").getMessageKey());
            assertEquals(TeamMessages.NAME_TOO_LONG, manager.createTeam(alice, "abcdefghi").getMessageKey());
            assertEquals(TeamMessages.NAME_INVALID_CHARACTERS, manager.createTeam(alice, "ab_cd").getMessageKey());
            assertEquals(TeamMessages.NAME_BLACKLISTED, manager.createTeam(alice, "Staff").getMessageKey());
            assertTrue(manager.createTeam(alice, "Wizards").isSuccess());
        }

        @Test
        void startingPointsComeFromConfig() {
            reconfigure(withPoints(new TeamSettings.PointsRules(250L, 0L)));

            assertEquals(250L, createTeam(alice, "Wizards").getPoints());
        }

        @Test
        void systemTeamsIgnoreTheNameBlacklistButNotTheOtherRules() {
            reconfigure(withNames(new TeamSettings.NameRules(
                    3, 16, Pattern.compile("^[A-Za-z]+$"), Set.of("spawn"))));

            TeamResult allowed = manager.createSystemTeam("Spawn");
            assertTrue(allowed.isSuccess());
            assertEquals(TeamType.SYSTEM, allowed.getTeam().orElseThrow().getType());

            assertEquals(TeamMessages.NAME_TOO_SHORT, manager.createSystemTeam("ab").getMessageKey());
        }

        @Test
        void aCancelledCreateEventLeavesNoTrace() {
            TeamManager cancelling = new TeamManager(() -> settings, TeamStore.NO_OP,
                    new RecordingDispatcher() {
                        @Override
                        public boolean callTeamCreate(Team team, UUID creator) {
                            return false;
                        }
                    }, now::get);

            TeamResult result = cancelling.createTeam(alice, "Wizards");

            assertEquals(TeamMessages.CREATE_CANCELLED, result.getMessageKey());
            assertEquals(0, cancelling.getTeamCount());
            assertFalse(cancelling.hasTeam(alice));
        }
    }

    @Nested
    class Disband {

        @Test
        void disbandingReleasesEveryMemberAndQueuesTheDeletion() {
            Team team = createTeam(alice, "Wizards");
            manager.invite(team, alice, bob);
            manager.join(bob, team, false);

            assertTrue(manager.disband(team, alice).isSuccess());

            assertFalse(manager.hasTeam(alice));
            assertFalse(manager.hasTeam(bob));
            assertTrue(manager.getTeamByName("Wizards").isEmpty());
            assertEquals(0, manager.getTeamCount());
        }

        @Test
        void aCoLeaderCannotDisbandByDefaultButStaffAlwaysCan() {
            Team team = createTeam(alice, "Wizards");
            manager.invite(team, alice, bob);
            manager.join(bob, team, false);
            manager.promote(team, alice, bob);

            assertEquals(TeamMessages.INSUFFICIENT_ROLE, manager.disband(team, bob).getMessageKey());
            assertTrue(manager.disband(team, null).isSuccess(), "null actor is the staff override");
        }

        @Test
        void disbandingClearsAlliancesHeldByOtherTeams() {
            Team wizards = createTeam(alice, "Wizards");
            Team warlocks = createTeam(bob, "Warlocks");
            manager.ally(wizards, alice, warlocks);
            manager.ally(warlocks, bob, wizards);
            assertTrue(warlocks.isAlliedWith(wizards.getId()));

            manager.disband(wizards, alice);

            assertFalse(warlocks.isAlliedWith(wizards.getId()));
        }

        @Test
        void systemTeamsAreProtectedFromPlayersButNotFromStaff() {
            Team spawn = manager.createSystemTeam("Spawn").getTeam().orElseThrow();

            assertEquals(TeamMessages.SYSTEM_TEAM_IMMUTABLE, manager.disband(spawn, alice).getMessageKey());
            assertTrue(manager.disband(spawn, null).isSuccess());
        }
    }

    @Nested
    class Rename {

        @Test
        void renameReindexesTheOldAndNewNames() {
            Team team = createTeam(alice, "Wizards");

            assertTrue(manager.rename(team, alice, "Warlocks").isSuccess());

            assertTrue(manager.getTeamByName("Wizards").isEmpty());
            assertSame(team, manager.getTeamByName("Warlocks").orElseThrow());
            assertEquals("Warlocks", team.getName());
        }

        @Test
        void renamingToTheSameNameIsRejectedButChangingCaseIsAllowed() {
            Team team = createTeam(alice, "Wizards");

            assertEquals(TeamMessages.RENAME_SAME_NAME, manager.rename(team, alice, "Wizards").getMessageKey());
            assertTrue(manager.rename(team, alice, "WIZARDS").isSuccess(), "a team may restyle its own name");
            assertSame(team, manager.getTeamByName("wizards").orElseThrow());
        }

        @Test
        void anotherTeamsNameIsStillRefused() {
            createTeam(alice, "Wizards");
            Team warlocks = createTeam(bob, "Warlocks");

            assertEquals(TeamMessages.NAME_TAKEN, manager.rename(warlocks, bob, "Wizards").getMessageKey());
        }
    }

    @Nested
    class Invites {

        @Test
        void inviteThenJoin() {
            Team team = createTeam(alice, "Wizards");

            assertTrue(manager.invite(team, alice, bob).isSuccess());
            assertTrue(manager.hasInvite(bob, team));
            assertEquals(java.util.List.of(team), manager.getPendingInvites(bob));

            assertTrue(manager.join(bob, team, false).isSuccess());
            assertEquals(TeamRole.MEMBER, team.getRole(bob).orElseThrow());
            assertSame(team, manager.getTeamOf(bob).orElseThrow());
            assertFalse(manager.hasInvite(bob, team), "the invite is consumed on join");
        }

        @Test
        void joiningWithoutAnInviteIsRefusedUnlessForced() {
            Team team = createTeam(alice, "Wizards");

            assertEquals(TeamMessages.JOIN_NO_INVITE, manager.join(bob, team, false).getMessageKey());
            assertTrue(manager.join(bob, team, true).isSuccess(), "forcejoin bypasses the invite");
        }

        @Test
        void invitesExpireAfterTheConfiguredDelay() {
            Team team = createTeam(alice, "Wizards");
            manager.invite(team, alice, bob);

            now.addAndGet(settings.inviteExpirySeconds() * 1000L);

            assertFalse(manager.hasInvite(bob, team));
            assertEquals(TeamMessages.JOIN_NO_INVITE, manager.join(bob, team, false).getMessageKey());
        }

        @Test
        void anExpiryOfZeroMeansInvitesNeverExpire() {
            reconfigure(withInviteExpiry(0L));
            Team team = createTeam(alice, "Wizards");
            manager.invite(team, alice, bob);

            now.addAndGet(365L * 24 * 3600 * 1000);

            assertTrue(manager.hasInvite(bob, team));
        }

        @Test
        void cannotInviteSomeoneWhoAlreadyHasATeam() {
            Team wizards = createTeam(alice, "Wizards");
            createTeam(bob, "Warlocks");

            assertEquals(TeamMessages.INVITE_TARGET_IN_TEAM, manager.invite(wizards, alice, bob).getMessageKey());
        }

        @Test
        void invitesAreRefusedOnceTheTeamIsFull() {
            reconfigure(withMaxMembers(1));
            Team team = createTeam(alice, "Wizards");

            assertEquals(TeamMessages.TEAM_FULL, manager.invite(team, alice, bob).getMessageKey());
        }

        @Test
        void forcejoinStillRespectsTheMemberCap() {
            reconfigure(withMaxMembers(1));
            Team team = createTeam(alice, "Wizards");

            assertEquals(TeamMessages.TEAM_FULL, manager.join(bob, team, true).getMessageKey());
            assertFalse(manager.hasTeam(bob));
        }

        @Test
        void revokingAnInviteRemovesIt() {
            Team team = createTeam(alice, "Wizards");
            manager.invite(team, alice, bob);

            assertTrue(manager.revokeInvite(team, alice, bob).isSuccess());
            assertFalse(manager.hasInvite(bob, team));
            assertEquals(TeamMessages.INVITE_NOT_FOUND, manager.revokeInvite(team, alice, bob).getMessageKey());
        }

        @Test
        void disbandingATeamWithdrawsItsPendingInvites() {
            Team team = createTeam(alice, "Wizards");
            manager.invite(team, alice, bob);

            manager.disband(team, alice);

            assertTrue(manager.getPendingInvites(bob).isEmpty());
        }

        @Test
        void aMemberCannotInviteWhenTheConfigRequiresCoLeader() {
            Team team = createTeam(alice, "Wizards");
            manager.join(bob, team, true);

            assertEquals(TeamMessages.INSUFFICIENT_ROLE, manager.invite(team, bob, carol).getMessageKey());
        }
    }

    @Nested
    class LeavingAndKicking {

        @Test
        void aMemberCanLeave() {
            Team team = createTeam(alice, "Wizards");
            manager.join(bob, team, true);

            assertTrue(manager.leave(bob).isSuccess());
            assertFalse(team.isMember(bob));
            assertFalse(manager.hasTeam(bob));
        }

        @Test
        void aLeaderMustHandOverBeforeLeaving() {
            Team team = createTeam(alice, "Wizards");
            manager.join(bob, team, true);

            assertEquals(TeamMessages.LEAVE_LEADER_MUST_TRANSFER, manager.leave(alice).getMessageKey());

            manager.transferLeadership(team, alice, bob);
            assertTrue(manager.leave(alice).isSuccess());
        }

        @Test
        void theLastMemberLeavingDisbandsTheTeamWhenConfigured() {
            Team team = createTeam(alice, "Wizards");

            TeamResult result = manager.leave(alice);

            assertTrue(result.isSuccess());
            assertEquals(TeamMessages.LEAVE_DISBANDED, result.getMessageKey());
            assertEquals(0, manager.getTeamCount());
        }

        @Test
        void theLastMemberCanLeaveAnEmptyTeamBehindWhenConfiguredTo() {
            reconfigure(withDisbandOnLastMemberLeave(false));
            Team team = createTeam(alice, "Wizards");

            assertEquals(TeamMessages.LEAVE_SUCCESS, manager.leave(alice).getMessageKey());
            assertEquals(1, manager.getTeamCount());
            assertEquals(0, team.getMemberCount());
        }

        @Test
        void leavingWithoutATeamFails() {
            assertEquals(TeamMessages.NOT_IN_TEAM, manager.leave(alice).getMessageKey());
        }

        @Test
        void kickingRequiresOutrankingTheTarget() {
            Team team = createTeam(alice, "Wizards");
            manager.join(bob, team, true);
            manager.join(carol, team, true);
            manager.promote(team, alice, bob);
            manager.promote(team, alice, carol);

            assertEquals(TeamMessages.INSUFFICIENT_ROLE, manager.kick(team, bob, carol).getMessageKey(),
                    "a co-leader cannot kick another co-leader");
            assertTrue(manager.kick(team, alice, carol).isSuccess());
        }

        @Test
        void nobodyCanKickThemselves() {
            Team team = createTeam(alice, "Wizards");

            assertEquals(TeamMessages.CANNOT_TARGET_SELF, manager.kick(team, alice, alice).getMessageKey());
        }

        @Test
        void kickingTheLastMemberDisbandsWithoutEmptyingTheTeamFirst() {
            // Removing first fired the leave event on an empty team, whose DTR ceiling
            // was then zero: "Delta is now RAIDABLE" went to the whole server just
            // before the team vanished (found in game, 15/09/2026).
            List<Integer> membersAtLeave = new ArrayList<>();
            TeamManager watched = new TeamManager(() -> settings, TeamStore.NO_OP, new RecordingDispatcher() {
                @Override
                public void callPlayerLeaveTeam(Team team, UUID player, LeaveCause cause) {
                    membersAtLeave.add(team.getMemberCount());
                }
            }, now::get);
            Team team = watched.createTeam(alice, "Wizards").getTeam().orElseThrow();

            assertTrue(watched.kick(team, null, alice).isSuccess());

            assertTrue(watched.getTeamByName("Wizards").isEmpty(), "disbanded, as /team leave would");
            assertTrue(watched.getTeamOf(alice).isEmpty());
            assertEquals(List.of(1), membersAtLeave, "no listener ever saw the team empty");
        }

        @Test
        void staffCanKickAnyoneIncludingTheLeader() {
            Team team = createTeam(alice, "Wizards");
            manager.join(bob, team, true);

            assertTrue(manager.kick(team, null, alice).isSuccess());
            assertFalse(team.isMember(alice));
            assertTrue(team.isMember(bob));
            // A team with no leader could not be disbanded, nor its bank emptied, nor
            // a role changed (found in the command review, 15/09/2026).
            assertEquals(bob, team.getLeader().orElseThrow(), "the remaining member takes over");
        }

        @Test
        void aKickedLeaderIsSucceededByACoLeaderFirst() {
            Team team = createTeam(alice, "Wizards");
            manager.join(bob, team, true);
            manager.join(carol, team, true);
            manager.promote(team, alice, carol);

            manager.kick(team, null, alice);

            assertEquals(carol, team.getLeader().orElseThrow(), "the co-leader, not the member");
            assertEquals(TeamRole.MEMBER, team.getRole(bob).orElseThrow());
            assertEquals(1, team.getMembersWithRole(TeamRole.LEADER).size(), "never two leaders");
        }
    }

    @Nested
    class Roles {

        @Test
        void promotionStopsBelowLeader() {
            Team team = createTeam(alice, "Wizards");
            manager.join(bob, team, true);

            assertTrue(manager.promote(team, alice, bob).isSuccess());
            assertEquals(TeamRole.CO_LEADER, team.getRole(bob).orElseThrow());

            assertEquals(TeamMessages.PROMOTE_ALREADY_HIGHEST, manager.promote(team, alice, bob).getMessageKey());
            assertEquals(1, team.getMembersWithRole(TeamRole.LEADER).size(), "a team never has two leaders");
        }

        @Test
        void theCoLeaderLimitIsEnforced() {
            reconfigure(withMaxCoLeaders(1));
            Team team = createTeam(alice, "Wizards");
            manager.join(bob, team, true);
            manager.join(carol, team, true);

            assertTrue(manager.promote(team, alice, bob).isSuccess());
            assertEquals(TeamMessages.PROMOTE_CO_LEADER_LIMIT, manager.promote(team, alice, carol).getMessageKey());
        }

        @Test
        void aLeaderCannotBeDemoted() {
            Team team = createTeam(alice, "Wizards");
            manager.join(bob, team, true);
            manager.promote(team, alice, bob);

            assertEquals(TeamMessages.DEMOTE_CANNOT_DEMOTE_LEADER,
                    manager.demote(team, null, alice).getMessageKey());
            assertTrue(manager.demote(team, alice, bob).isSuccess());
            assertEquals(TeamRole.MEMBER, team.getRole(bob).orElseThrow());
        }

        @Test
        void transferSwapsTheLeaderAndDemotesTheFormerOneAsConfigured() {
            Team team = createTeam(alice, "Wizards");
            manager.join(bob, team, true);

            assertTrue(manager.transferLeadership(team, alice, bob).isSuccess());

            assertEquals(bob, team.getLeader().orElseThrow());
            assertEquals(TeamRole.LEADER, team.getRole(bob).orElseThrow());
            assertEquals(TeamRole.CO_LEADER, team.getRole(alice).orElseThrow());
        }

        @Test
        void transferTargetMustBeAMember() {
            Team team = createTeam(alice, "Wizards");

            assertEquals(TeamMessages.TARGET_NOT_IN_TEAM,
                    manager.transferLeadership(team, alice, bob).getMessageKey());
        }
    }

    @Nested
    class Alliances {

        @Test
        void allianceNeedsBothSidesToAgree() {
            Team wizards = createTeam(alice, "Wizards");
            Team warlocks = createTeam(bob, "Warlocks");

            TeamResult request = manager.ally(wizards, alice, warlocks);
            assertEquals(TeamMessages.ALLY_REQUEST_SENT, request.getMessageKey());
            assertFalse(wizards.isAlliedWith(warlocks.getId()), "an offer is not an alliance");

            TeamResult accepted = manager.ally(warlocks, bob, wizards);
            assertEquals(TeamMessages.ALLY_NOW_ALLIED, accepted.getMessageKey());
            assertTrue(wizards.isAlliedWith(warlocks.getId()));
            assertTrue(warlocks.isAlliedWith(wizards.getId()), "alliances are symmetric");
        }

        @Test
        void repeatingAnOfferDoesNotSelfAccept() {
            Team wizards = createTeam(alice, "Wizards");
            Team warlocks = createTeam(bob, "Warlocks");
            manager.ally(wizards, alice, warlocks);

            assertEquals(TeamMessages.ALLY_REQUEST_ALREADY_SENT,
                    manager.ally(wizards, alice, warlocks).getMessageKey());
            assertFalse(wizards.isAlliedWith(warlocks.getId()));
        }

        @Test
        void theAllyLimitIsCheckedOnBothSides() {
            reconfigure(withAlliances(new TeamSettings.AllianceRules(true, 1)));
            Team wizards = createTeam(alice, "Wizards");
            Team warlocks = createTeam(bob, "Warlocks");
            Team clerics = createTeam(carol, "Clerics");
            manager.ally(wizards, alice, warlocks);
            manager.ally(warlocks, bob, wizards);

            assertEquals(TeamMessages.ALLY_LIMIT_REACHED, manager.ally(wizards, alice, clerics).getMessageKey());
            assertEquals(TeamMessages.ALLY_TARGET_LIMIT_REACHED,
                    manager.ally(clerics, carol, wizards).getMessageKey());
        }

        @Test
        void alliancesCanBeDisabledEntirely() {
            reconfigure(withAlliances(new TeamSettings.AllianceRules(false, 0)));
            Team wizards = createTeam(alice, "Wizards");
            Team warlocks = createTeam(bob, "Warlocks");

            assertEquals(TeamMessages.ALLY_DISABLED, manager.ally(wizards, alice, warlocks).getMessageKey());
        }

        @Test
        void unallyBreaksBothSidesAndAlsoCancelsAPendingOffer() {
            Team wizards = createTeam(alice, "Wizards");
            Team warlocks = createTeam(bob, "Warlocks");
            manager.ally(wizards, alice, warlocks);
            manager.ally(warlocks, bob, wizards);

            assertTrue(manager.unally(wizards, alice, warlocks).isSuccess());
            assertFalse(wizards.isAlliedWith(warlocks.getId()));
            assertFalse(warlocks.isAlliedWith(wizards.getId()));

            assertEquals(TeamMessages.UNALLY_NOT_ALLIED, manager.unally(wizards, alice, warlocks).getMessageKey());

            manager.ally(wizards, alice, warlocks);
            assertTrue(manager.unally(wizards, alice, warlocks).isSuccess(), "a pending offer can be withdrawn");
        }

        @Test
        void ateamCannotAllyItself() {
            Team wizards = createTeam(alice, "Wizards");

            assertEquals(TeamMessages.ALLY_SAME_TEAM, manager.ally(wizards, alice, wizards).getMessageKey());
        }
    }

    @Nested
    class Relations {

        @Test
        void relationsReflectMembershipAlliancesAndSystemTeams() {
            Team wizards = createTeam(alice, "Wizards");
            Team warlocks = createTeam(bob, "Warlocks");
            Team spawn = manager.createSystemTeam("Spawn").getTeam().orElseThrow();

            assertEquals(TeamRelation.SELF, manager.getRelation(wizards, wizards));
            assertEquals(TeamRelation.ENEMY, manager.getRelation(wizards, warlocks));
            assertEquals(TeamRelation.SYSTEM, manager.getRelation(wizards, spawn));
            assertEquals(TeamRelation.NEUTRAL, manager.getRelation(wizards, null));

            manager.ally(wizards, alice, warlocks);
            manager.ally(warlocks, bob, wizards);
            assertEquals(TeamRelation.ALLY, manager.getRelation(wizards, warlocks));
        }

        @Test
        void relationByPlayerFallsBackToNeutralForTeamlessPlayers() {
            createTeam(alice, "Wizards");

            assertEquals(TeamRelation.NEUTRAL, manager.getRelation(alice, bob));
            assertEquals(TeamRelation.SELF, manager.getRelation(alice, alice));
        }
    }

    @Nested
    class Focus {

        @Test
        void focusAndUnfocusATeam() {
            Team wizards = createTeam(alice, "Wizards");
            Team warlocks = createTeam(bob, "Warlocks");

            assertTrue(manager.focusTeam(wizards, alice, warlocks).isSuccess());
            assertTrue(wizards.isFocusingTeam(warlocks.getId()));

            assertEquals(TeamMessages.FOCUS_ALREADY_FOCUSED,
                    manager.focusTeam(wizards, alice, warlocks).getMessageKey());

            assertTrue(manager.unfocus(wizards, alice, warlocks.getId(), "Warlocks").isSuccess());
            assertFalse(wizards.isFocusingTeam(warlocks.getId()));
        }

        @Test
        void aTeamCannotFocusItselfOrItsOwnMembers() {
            Team wizards = createTeam(alice, "Wizards");
            manager.join(bob, wizards, true);

            assertEquals(TeamMessages.FOCUS_CANNOT_FOCUS_OWN_TEAM,
                    manager.focusTeam(wizards, alice, wizards).getMessageKey());
            assertEquals(TeamMessages.FOCUS_CANNOT_FOCUS_OWN_TEAM,
                    manager.focusPlayer(wizards, alice, bob, "Bob").getMessageKey());
        }

        @Test
        void aFocusedPlayerWhoJoinsIsNoLongerFocused() {
            Team wizards = createTeam(alice, "Wizards");
            assertTrue(manager.focusPlayer(wizards, alice, bob, "Bob").isSuccess());

            manager.join(bob, wizards, true);

            assertFalse(wizards.isFocusingPlayer(bob));
        }

        @Test
        void theFocusLimitIsEnforcedAcrossTeamsAndPlayers() {
            reconfigure(withFocus(new TeamSettings.FocusRules(true, 1)));
            Team wizards = createTeam(alice, "Wizards");
            Team warlocks = createTeam(bob, "Warlocks");

            assertTrue(manager.focusTeam(wizards, alice, warlocks).isSuccess());
            assertEquals(TeamMessages.FOCUS_LIMIT_REACHED,
                    manager.focusPlayer(wizards, alice, carol, "Carol").getMessageKey());
        }

        @Test
        void focusCanBeDisabled() {
            reconfigure(withFocus(new TeamSettings.FocusRules(false, 0)));
            Team wizards = createTeam(alice, "Wizards");
            Team warlocks = createTeam(bob, "Warlocks");

            assertEquals(TeamMessages.FOCUS_DISABLED, manager.focusTeam(wizards, alice, warlocks).getMessageKey());
        }
    }

    @Nested
    class Rally {

        @Test
        void rallyExpiresAfterTheConfiguredDuration() {
            Team team = createTeam(alice, "Wizards");
            WorldPosition point = WorldPosition.of("world", 10, 64, 20);

            assertTrue(manager.setRally(team, alice, point).isSuccess());
            assertEquals(point, manager.getRally(team).orElseThrow());

            now.addAndGet(settings.rally().durationSeconds() * 1000L);

            assertTrue(manager.getRally(team).isEmpty());
            assertTrue(team.getRally().isEmpty(), "an expired rally is cleared, not just hidden");
        }

        @Test
        void aDurationOfZeroMeansTheRallyNeverExpires() {
            reconfigure(withRally(new TeamSettings.RallyRules(true, 0L)));
            Team team = createTeam(alice, "Wizards");
            manager.setRally(team, alice, WorldPosition.of("world", 0, 64, 0));

            now.addAndGet(30L * 24 * 3600 * 1000);

            assertTrue(manager.getRally(team).isPresent());
        }

        @Test
        void clearingAnUnsetRallyFails() {
            Team team = createTeam(alice, "Wizards");

            assertEquals(TeamMessages.RALLY_NOT_SET, manager.clearRally(team, alice).getMessageKey());
        }

        @Test
        void rallyCanBeDisabled() {
            reconfigure(withRally(new TeamSettings.RallyRules(false, 300L)));
            Team team = createTeam(alice, "Wizards");

            assertEquals(TeamMessages.RALLY_DISABLED,
                    manager.setRally(team, alice, WorldPosition.of("world", 0, 64, 0)).getMessageKey());
        }
    }

    @Nested
    class BankPointsAndKoth {

        @Test
        void depositAndWithdraw() {
            Team team = createTeam(alice, "Wizards");

            assertTrue(manager.depositToBank(team, alice, 500.0).isSuccess());
            assertEquals(500.0, team.getBalance());

            assertTrue(manager.withdrawFromBank(team, alice, 200.0).isSuccess());
            assertEquals(300.0, team.getBalance());
        }

        @Test
        void overdraftAndNonPositiveAmountsAreRefused() {
            Team team = createTeam(alice, "Wizards");
            manager.depositToBank(team, alice, 100.0);

            assertEquals(TeamMessages.BANK_INSUFFICIENT_FUNDS,
                    manager.withdrawFromBank(team, alice, 100.01).getMessageKey());
            assertEquals(TeamMessages.BANK_INVALID_AMOUNT, manager.depositToBank(team, alice, 0.0).getMessageKey());
            assertEquals(TeamMessages.BANK_INVALID_AMOUNT, manager.depositToBank(team, alice, -5.0).getMessageKey());
            assertEquals(TeamMessages.BANK_INVALID_AMOUNT,
                    manager.depositToBank(team, alice, Double.NaN).getMessageKey());
            assertEquals(100.0, team.getBalance(), "a rejected operation must not move the balance");
        }

        @Test
        void withdrawingRequiresLeaderByDefault() {
            Team team = createTeam(alice, "Wizards");
            manager.join(bob, team, true);
            manager.depositToBank(team, bob, 50.0);

            assertEquals(TeamMessages.INSUFFICIENT_ROLE,
                    manager.withdrawFromBank(team, bob, 10.0).getMessageKey());
        }

        @Test
        void pointsHoldAtTheLimitRatherThanWrapRound() {
            // /team addpoints with a huge number turned the points negative, then
            // clamped them to the floor (found in the command review, 15/09/2026).
            Team team = createTeam(alice, "Wizards");
            manager.setPoints(team, Long.MAX_VALUE - 5L);

            manager.addPoints(team, 1_000L);

            assertEquals(Long.MAX_VALUE, team.getPoints());
            assertEquals(Long.MIN_VALUE, TeamManager.saturatedAdd(Long.MIN_VALUE + 1L, -10L));
            assertEquals(-3L, TeamManager.saturatedAdd(2L, -5L), "no overflow, no change");
        }

        @Test
        void aBankStopsShortOfInfinity() {
            Team team = createTeam(alice, "Wizards");
            assertTrue(manager.depositToBank(team, alice, Double.MAX_VALUE).isSuccess());

            assertEquals(TeamMessages.BANK_INVALID_AMOUNT,
                    manager.depositToBank(team, alice, Double.MAX_VALUE).getMessageKey());
            assertEquals(Double.MAX_VALUE, team.getBalance());
        }

        @Test
        void pointsNeverFallBelowTheConfiguredFloor() {
            Team team = createTeam(alice, "Wizards");
            manager.addPoints(team, 10L);

            manager.addPoints(team, -100L);

            assertEquals(0L, team.getPoints());
        }

        @Test
        void staffCanOverridePoints() {
            Team team = createTeam(alice, "Wizards");

            assertTrue(manager.setPoints(team, 1234L).isSuccess());
            assertEquals(1234L, team.getPoints());
        }

        @Test
        void kothCapturesStopCountingAtTheCap() {
            reconfigure(withKoth(new TeamSettings.KothRules(2, 10L)));
            Team team = createTeam(alice, "Wizards");

            assertTrue(manager.recordKothCapture(team).isSuccess());
            assertTrue(manager.recordKothCapture(team).isSuccess());
            assertEquals(20L, team.getPoints());

            TeamResult capped = manager.recordKothCapture(team);
            assertEquals(TeamMessages.KOTH_CAP_REACHED, capped.getMessageKey());
            assertEquals(2, team.getKothCaptures(), "a capped capture must not increment the counter");
            assertEquals(20L, team.getPoints(), "a capped capture must not award points");
        }

        @Test
        void aCapOfZeroMeansUncapped() {
            reconfigure(withKoth(new TeamSettings.KothRules(0, 5L)));
            Team team = createTeam(alice, "Wizards");

            for (int i = 0; i < 10; i++) {
                assertTrue(manager.recordKothCapture(team).isSuccess());
            }
            assertEquals(10, team.getKothCaptures());
            assertEquals(50L, team.getPoints());
        }

        @Test
        void kothCapturesCanBeResetForANewPeriod() {
            reconfigure(withKoth(new TeamSettings.KothRules(1, 0L)));
            Team team = createTeam(alice, "Wizards");
            manager.recordKothCapture(team);

            manager.resetKothCaptures();

            assertEquals(0, team.getKothCaptures());
            assertTrue(manager.recordKothCapture(team).isSuccess());
        }
    }

    @Nested
    class Ranking {

        @Test
        void topTeamsAreSortedByPointsAndExcludeSystemTeams() {
            Team wizards = createTeam(alice, "Wizards");
            Team warlocks = createTeam(bob, "Warlocks");
            Team clerics = createTeam(carol, "Clerics");
            Team spawn = manager.createSystemTeam("Spawn").getTeam().orElseThrow();
            manager.setPoints(wizards, 10L);
            manager.setPoints(warlocks, 30L);
            manager.setPoints(clerics, 20L);
            manager.setPoints(spawn, 999L);

            assertEquals(java.util.List.of(warlocks, clerics, wizards), manager.getTopTeamsByPoints(0));
            assertEquals(java.util.List.of(warlocks, clerics), manager.getTopTeamsByPoints(2));
        }

        @Test
        void tiesBreakOnNameSoTheRankingIsStable() {
            Team wizards = createTeam(alice, "Wizards");
            Team clerics = createTeam(bob, "Clerics");
            manager.setPoints(wizards, 5L);
            manager.setPoints(clerics, 5L);

            assertEquals(java.util.List.of(clerics, wizards), manager.getTopTeamsByPoints(0));
        }
    }

    @Nested
    class ChatChannels {

        @Test
        void channelDefaultsToPublicAndResetsWhenLeavingTheTeam() {
            Team team = createTeam(alice, "Wizards");
            manager.join(bob, team, true);
            assertEquals(ChatChannel.PUBLIC, manager.getChatChannel(bob));

            manager.setChatChannel(bob, ChatChannel.TEAM);
            assertEquals(ChatChannel.TEAM, manager.getChatChannel(bob));

            manager.leave(bob);
            assertEquals(ChatChannel.PUBLIC, manager.getChatChannel(bob));
        }
    }

    /**
     * Server land is either a safe zone (spawn) or a combat zone (warzone, roads,
     * event grounds). The PvP module asks {@link Team#isSafeZone()} on every hit.
     */
    @Nested
    class SystemZones {

        @Test
        void aSystemTeamIsASafeZoneUnlessToldOtherwise() {
            Team spawn = manager.createSystemTeam("Spawn").getTeam().orElseThrow();
            assertEquals(java.util.Optional.of(SystemZone.SAFE), spawn.getSystemZone());
            assertTrue(spawn.isSafeZone(), "what every system team was before combat zones existed");
        }

        @Test
        void aCombatZoneIsServerLandPlayersFightOn() {
            TeamResult result = manager.createSystemTeam("Warzone", SystemZone.COMBAT);

            assertEquals(TeamMessages.SYSTEM_CREATED_COMBAT, result.getMessageKey());
            Team warzone = result.getTeam().orElseThrow();
            assertEquals(TeamType.SYSTEM, warzone.getType());
            assertFalse(warzone.isSafeZone());
        }

        @Test
        void staffCanSwitchAServerTeamBetweenTheTwo() {
            Team roads = manager.createSystemTeam("Roads").getTeam().orElseThrow();
            roads.clearDirty();

            assertEquals(TeamMessages.SYSTEM_ZONE_COMBAT, manager.setSystemZone(roads, SystemZone.COMBAT).getMessageKey());
            assertFalse(roads.isSafeZone());
            assertTrue(roads.isDirty(), "the change must reach storage");

            manager.setSystemZone(roads, SystemZone.SAFE);
            assertTrue(roads.isSafeZone());
        }

        @Test
        void aPlayerTeamHasNoZoneKindAndCannotBeGivenOne() {
            Team wizards = createTeam(alice, "Wizards");

            assertEquals(java.util.Optional.empty(), wizards.getSystemZone());
            assertFalse(wizards.isSafeZone(), "a player's territory is never a safe zone");
            assertEquals(TeamMessages.NOT_SYSTEM_TEAM,
                    manager.setSystemZone(wizards, SystemZone.SAFE).getMessageKey());
        }

        @Test
        void zoneKindsAreNamedCaseInsensitively() {
            assertEquals(java.util.Optional.of(SystemZone.COMBAT), SystemZone.fromId(" Combat "));
            assertEquals(java.util.Optional.empty(), SystemZone.fromId("pvp"));
            assertEquals("safe", SystemZone.SAFE.id());
        }
    }

    @Nested
    class Persistence {

        @Test
        void flushWritesOnlyDirtyTeamsAndDeletesDisbandedOnes() throws Exception {
            RecordingStore store = new RecordingStore();
            TeamManager persisting = new TeamManager(() -> settings, store, TeamEventDispatcher.NO_OP, now::get);

            Team team = persisting.createTeam(alice, "Wizards").getTeam().orElseThrow();
            assertEquals(1, persisting.flush());
            assertEquals(java.util.List.of(team.getId()), store.saved);

            store.saved.clear();
            assertEquals(0, persisting.flush(), "nothing changed, nothing written");

            persisting.addPoints(team, 5L);
            assertEquals(1, persisting.flush());

            persisting.disband(team, alice);
            persisting.flush();
            assertEquals(java.util.List.of(team.getId()), store.deleted);
        }

        @Test
        void aFailedWriteLeavesTheTeamDirtyForTheNextFlush() {
            RecordingStore store = new RecordingStore();
            store.failOnSave = true;
            TeamManager persisting = new TeamManager(() -> settings, store, TeamEventDispatcher.NO_OP, now::get);
            Team team = persisting.createTeam(alice, "Wizards").getTeam().orElseThrow();

            assertThrows(IllegalStateException.class, persisting::flush);
            assertTrue(team.isDirty(), "an unwritten change must not be silently dropped");
        }

        @Test
        void loadAllRebuildsEveryIndex() throws Exception {
            RecordingStore store = new RecordingStore();
            Team stored = new Team(UUID.randomUUID(), "Wizards", TeamType.PLAYER, alice, 1L);
            stored.addMember(bob, TeamRole.CO_LEADER);
            store.preloaded.add(stored);

            TeamManager loading = new TeamManager(() -> settings, store, TeamEventDispatcher.NO_OP, now::get);
            loading.loadAll();

            assertSame(stored, loading.getTeamByName("wizards").orElseThrow());
            assertSame(stored, loading.getTeamOf(alice).orElseThrow());
            assertSame(stored, loading.getTeamOf(bob).orElseThrow());
            assertFalse(stored.isDirty(), "a freshly loaded team is not pending a write");
        }
    }

    @Test
    void configChangesTakeEffectWithoutRecreatingTheManager() {
        Team team = createTeam(alice, "Wizards");
        manager.join(bob, team, true);

        assertEquals(TeamMessages.INSUFFICIENT_ROLE, manager.invite(team, bob, carol).getMessageKey());

        Map<TeamAction, TeamRole> roles = new EnumMap<>(settings.requiredRoles());
        roles.put(TeamAction.INVITE, TeamRole.MEMBER);
        reconfigure(withRequiredRoles(roles));

        assertTrue(manager.invite(team, bob, carol).isSuccess(), "/hcf reload must be enough");
    }

    // --- settings helpers -------------------------------------------------

    private TeamSettings withNames(TeamSettings.NameRules names) {
        return new TeamSettings(names, settings.maxMembers(), settings.maxCoLeaders(),
                settings.inviteExpirySeconds(), settings.disbandOnLastMemberLeave(),
                settings.roleAfterLeadershipTransfer(), settings.requiredRoles(), settings.alliances(),
                settings.focus(), settings.rally(), settings.bank(), settings.points(), settings.koth());
    }

    private TeamSettings withMaxMembers(int maxMembers) {
        return new TeamSettings(settings.names(), maxMembers, settings.maxCoLeaders(),
                settings.inviteExpirySeconds(), settings.disbandOnLastMemberLeave(),
                settings.roleAfterLeadershipTransfer(), settings.requiredRoles(), settings.alliances(),
                settings.focus(), settings.rally(), settings.bank(), settings.points(), settings.koth());
    }

    private TeamSettings withMaxCoLeaders(int maxCoLeaders) {
        return new TeamSettings(settings.names(), settings.maxMembers(), maxCoLeaders,
                settings.inviteExpirySeconds(), settings.disbandOnLastMemberLeave(),
                settings.roleAfterLeadershipTransfer(), settings.requiredRoles(), settings.alliances(),
                settings.focus(), settings.rally(), settings.bank(), settings.points(), settings.koth());
    }

    private TeamSettings withInviteExpiry(long seconds) {
        return new TeamSettings(settings.names(), settings.maxMembers(), settings.maxCoLeaders(),
                seconds, settings.disbandOnLastMemberLeave(),
                settings.roleAfterLeadershipTransfer(), settings.requiredRoles(), settings.alliances(),
                settings.focus(), settings.rally(), settings.bank(), settings.points(), settings.koth());
    }

    private TeamSettings withDisbandOnLastMemberLeave(boolean disband) {
        return new TeamSettings(settings.names(), settings.maxMembers(), settings.maxCoLeaders(),
                settings.inviteExpirySeconds(), disband,
                settings.roleAfterLeadershipTransfer(), settings.requiredRoles(), settings.alliances(),
                settings.focus(), settings.rally(), settings.bank(), settings.points(), settings.koth());
    }

    private TeamSettings withRequiredRoles(Map<TeamAction, TeamRole> roles) {
        return new TeamSettings(settings.names(), settings.maxMembers(), settings.maxCoLeaders(),
                settings.inviteExpirySeconds(), settings.disbandOnLastMemberLeave(),
                settings.roleAfterLeadershipTransfer(), roles, settings.alliances(),
                settings.focus(), settings.rally(), settings.bank(), settings.points(), settings.koth());
    }

    private TeamSettings withAlliances(TeamSettings.AllianceRules alliances) {
        return new TeamSettings(settings.names(), settings.maxMembers(), settings.maxCoLeaders(),
                settings.inviteExpirySeconds(), settings.disbandOnLastMemberLeave(),
                settings.roleAfterLeadershipTransfer(), settings.requiredRoles(), alliances,
                settings.focus(), settings.rally(), settings.bank(), settings.points(), settings.koth());
    }

    private TeamSettings withFocus(TeamSettings.FocusRules focus) {
        return new TeamSettings(settings.names(), settings.maxMembers(), settings.maxCoLeaders(),
                settings.inviteExpirySeconds(), settings.disbandOnLastMemberLeave(),
                settings.roleAfterLeadershipTransfer(), settings.requiredRoles(), settings.alliances(),
                focus, settings.rally(), settings.bank(), settings.points(), settings.koth());
    }

    private TeamSettings withRally(TeamSettings.RallyRules rally) {
        return new TeamSettings(settings.names(), settings.maxMembers(), settings.maxCoLeaders(),
                settings.inviteExpirySeconds(), settings.disbandOnLastMemberLeave(),
                settings.roleAfterLeadershipTransfer(), settings.requiredRoles(), settings.alliances(),
                settings.focus(), rally, settings.bank(), settings.points(), settings.koth());
    }

    /** A team bank reads in the economy's currency once it installs its format. */
    @Test
    void amountsReadInTheInstalledCurrency() {
        assertEquals("1234.50", manager.formatAmount(1234.5), "the plain default");
        manager.setMoneyFormat(amount -> String.format(java.util.Locale.ROOT, "$%,.2f", amount));
        assertEquals("$1,234.50", manager.formatAmount(1234.5));
    }

    /** The Team Points scale: what earns and costs points, all configuration. */
    @Nested
    class PointsScale {

        private void scale(long perKill, long perDeath, long perRaidable, long perConquestWin, long perKingWin) {
            reconfigure(withPoints(new TeamSettings.PointsRules(100L, 0L, perKill, perDeath, perRaidable,
                    perConquestWin, perKingWin, 0L, 0L, 0L, 0L)));
        }

        @Test
        void aKillPaysTheKillersTeamAndADeathCostsTheVictims() {
            scale(5L, -2L, 0L, 0L, 0L);
            Team wizards = createTeam(alice, "Wizards");
            Team knights = createTeam(bob, "Knights");
            manager.recordDeath(alice, bob);
            assertEquals(105L, wizards.getPoints());
            assertEquals(98L, knights.getPoints());
        }

        @Test
        void killingATeammateOrOneselfPaysNothing() {
            scale(5L, 0L, 0L, 0L, 0L);
            Team wizards = createTeam(alice, "Wizards");
            assertTrue(manager.join(carol, wizards, true).isSuccess());
            manager.recordDeath(alice, carol);
            manager.recordDeath(alice, alice);
            assertEquals(100L, wizards.getPoints());
        }

        @Test
        void aPlayerWithNoTeamStillCountsAsAKill() {
            scale(5L, -2L, 0L, 0L, 0L);
            Team wizards = createTeam(alice, "Wizards");
            manager.recordDeath(alice, carol);
            assertEquals(105L, wizards.getPoints(), "the victim has no team to lose anything");
        }

        @Test
        void aDeathWithNoKillerStillCosts() {
            scale(5L, -2L, 0L, 0L, 0L);
            Team knights = createTeam(bob, "Knights");
            manager.recordDeath(null, bob);
            assertEquals(98L, knights.getPoints(), "lava counts as a death");
        }

        @Test
        void raidableAndWinsAreScaledToo() {
            scale(0L, 0L, -10L, 25L, 15L);
            Team wizards = createTeam(alice, "Wizards");
            manager.recordRaidable(wizards);
            manager.recordConquestWin(wizards);
            manager.recordKingWin(wizards);
            assertEquals(130L, wizards.getPoints());
        }

        @Test
        void dtcLastBreakAndSlideWinsAreScaledToo() {
            reconfigure(withPoints(new TeamSettings.PointsRules(100L, 0L, 0L, 0L, 0L, 0L, 0L, 5L, 10L, 20L, 40L)));
            Team wizards = createTeam(alice, "Wizards");
            manager.recordDtcWin(wizards);
            manager.recordLastBreakWin(wizards);
            manager.recordSlideWin(wizards);
            manager.recordTotemWin(wizards);
            assertEquals(175L, wizards.getPoints());
        }

        /**
         * The shipped scale (the project owner's of 23/09/2026): a kill +1, a death
         * -2, and an event worth far more than the kills fought for it.
         */
        @Test
        void theShippedScalePaysKillsCostsDeathsAndWeighsEachEvent() {
            Team wizards = createTeam(alice, "Wizards");
            Team knights = createTeam(bob, "Knights");
            manager.recordKothCapture(knights);
            assertEquals(100L, knights.getPoints(), "a KOTH capture");
            manager.recordDeath(alice, bob);
            assertEquals(1L, wizards.getPoints(), "a kill");
            assertEquals(98L, knights.getPoints(), "a death");

            long before = wizards.getPoints();
            manager.recordKothCapture(wizards, true);
            manager.recordConquestWin(wizards);
            manager.recordDtcWin(wizards);
            manager.recordSlideWin(wizards);
            manager.recordKingWin(wizards);
            manager.recordLastBreakWin(wizards);
            manager.recordTotemWin(wizards, 5);
            manager.recordTotemWin(wizards, 3);
            // Citadel 300, Conquest 250, DTC and Slide 200, King and Last Break 150,
            // Totem 150, Mini Totem 80.
            assertEquals(before + 300 + 250 + 200 + 200 + 150 + 150 + 150 + 80, wizards.getPoints());
        }

        @Test
        void becomingRaidableCostsHalfThePointsAsShipped() {
            Team wizards = createTeam(alice, "Wizards");
            manager.recordKothCapture(wizards);
            manager.recordKothCapture(wizards);
            manager.recordDeath(null, alice);
            assertEquals(198L, wizards.getPoints());
            manager.recordRaidable(wizards);
            assertEquals(99L, wizards.getPoints(), "half, rounded in the team's favour");
        }

        @Test
        void aFixedLossAppliesWhenNoShareIsSet() {
            TeamSettings.PointsRules fixed = new TeamSettings.PointsRules(0L, 0L, 0L, 0L, -40L, 0L, 0L, 0L, 0L,
                    0L, 0L, 0L, 0L, 0.0);
            assertEquals(-40L, fixed.raidableDelta(1000L));
            TeamSettings.PointsRules share = new TeamSettings.PointsRules(0L, 0L, 0L, 0L, -40L, 0L, 0L, 0L, 0L,
                    0L, 0L, 0L, 0L, 25.0);
            assertEquals(-250L, share.raidableDelta(1000L), "a share replaces the fixed number");
            assertEquals(0L, share.raidableDelta(0L));
        }

        @Test
        void theFloorHolds() {
            scale(0L, -500L, 0L, 0L, 0L);
            Team knights = createTeam(bob, "Knights");
            manager.recordDeath(null, bob);
            assertEquals(0L, knights.getPoints());
        }

        @Test
        void countedCapturesAreResetForANewPeriod() {
            Team wizards = createTeam(alice, "Wizards");
            createTeam(bob, "Knights");
            manager.recordKothCapture(wizards);
            manager.recordKothCapture(wizards);
            assertEquals(1, manager.resetKothCaptures(), "only the team that had any");
            assertEquals(0, wizards.getKothCaptures());
            assertEquals(0, manager.resetKothCaptures());
        }
    }

    private TeamSettings withPoints(TeamSettings.PointsRules points) {
        return new TeamSettings(settings.names(), settings.maxMembers(), settings.maxCoLeaders(),
                settings.inviteExpirySeconds(), settings.disbandOnLastMemberLeave(),
                settings.roleAfterLeadershipTransfer(), settings.requiredRoles(), settings.alliances(),
                settings.focus(), settings.rally(), settings.bank(), points, settings.koth());
    }

    private TeamSettings withKoth(TeamSettings.KothRules koth) {
        return new TeamSettings(settings.names(), settings.maxMembers(), settings.maxCoLeaders(),
                settings.inviteExpirySeconds(), settings.disbandOnLastMemberLeave(),
                settings.roleAfterLeadershipTransfer(), settings.requiredRoles(), settings.alliances(),
                settings.focus(), settings.rally(), settings.bank(), settings.points(), koth);
    }

    // --- test doubles -----------------------------------------------------

    private static class RecordingStore implements TeamStore {
        final java.util.List<Team> preloaded = new java.util.ArrayList<>();
        final java.util.List<UUID> saved = new java.util.ArrayList<>();
        final java.util.List<UUID> deleted = new java.util.ArrayList<>();
        boolean failOnSave;

        @Override
        public void initSchema() {
        }

        @Override
        public java.util.Collection<Team> loadAll() {
            return java.util.List.copyOf(preloaded);
        }

        @Override
        public void save(Team team) {
            if (failOnSave) {
                throw new IllegalStateException("simulated write failure");
            }
            saved.add(team.getId());
        }

        @Override
        public void delete(UUID teamId) {
            deleted.add(teamId);
        }
    }

    private static class RecordingDispatcher implements TeamEventDispatcher {
        @Override
        public boolean callTeamCreate(Team team, UUID creator) {
            return true;
        }

        @Override
        public boolean callTeamDisband(Team team, UUID actor) {
            return true;
        }

        @Override
        public boolean callTeamRename(Team team, String newName, UUID actor) {
            return true;
        }

        @Override
        public void callPlayerJoinTeam(Team team, UUID player) {
        }

        @Override
        public void callPlayerLeaveTeam(Team team, UUID player, LeaveCause cause) {
        }

        @Override
        public void callRoleChange(Team team, UUID player, TeamRole previousRole, TeamRole newRole) {
        }

        @Override
        public void callAllianceChange(Team a, Team b, boolean allied) {
        }
    }
}
