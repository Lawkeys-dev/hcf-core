package com.lawkeys.hcfcore.claim;

import com.lawkeys.hcfcore.team.Team;
import com.lawkeys.hcfcore.team.TeamEventDispatcher;
import com.lawkeys.hcfcore.team.TeamManager;
import com.lawkeys.hcfcore.team.TeamMessages;
import com.lawkeys.hcfcore.team.TeamResult;
import com.lawkeys.hcfcore.team.TeamRole;
import com.lawkeys.hcfcore.team.TeamSettings;
import com.lawkeys.hcfcore.team.TeamStore;
import com.lawkeys.hcfcore.util.ChunkPosition;
import com.lawkeys.hcfcore.util.WorldPosition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rule-level tests for the territory module, with no server and no database.
 *
 * <p>The {@link Reclaim} group is the important one: it pins down the invariant
 * FEATURES.md section 3 specifies, namely that raiding opens land to pillage but
 * never transfers ownership.
 */
class ClaimManagerTest {

    private final AtomicLong now = new AtomicLong(1_000_000L);
    private final Set<UUID> raidableTeams = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private ClaimSettings settings;
    private TeamManager teamManager;
    private ClaimManager claims;

    private UUID alice;
    private UUID bob;
    private Team wizards;
    private Team warlocks;

    @BeforeEach
    void setUp() {
        settings = ClaimSettings.defaults();
        teamManager = new TeamManager(TeamSettings::defaults, TeamStore.NO_OP,
                TeamEventDispatcher.NO_OP, now::get);
        claims = new ClaimManager(() -> settings, teamManager, ClaimStore.NO_OP, now::get);
        claims.setRaidabilityPolicy(raidableTeams::contains);

        alice = UUID.randomUUID();
        bob = UUID.randomUUID();
        wizards = teamManager.createTeam(alice, "Wizards").getTeam().orElseThrow();
        warlocks = teamManager.createTeam(bob, "Warlocks").getTeam().orElseThrow();
    }

    private static ChunkPosition chunk(int x, int z) {
        return new ChunkPosition("world", x, z);
    }

    /** Claims one chunk with a staff override, bypassing role and placement friction. */
    private void give(Team team, ChunkPosition... positions) {
        TeamResult result = claims.claim(team, null, List.of(positions));
        assertTrue(result.isSuccess(), () -> "setup claim failed: " + result);
    }

    // ------------------------------------------------------------------

    /**
     * /team lockclaim, as the project owner set it on 12/09/2026: during SOTW only,
     * so nobody can camp in a team's claim until SOTW ends - nobody but members,
     * allies included.
     */
    @Nested
    class LockedClaims {

        private boolean sotw;

        @BeforeEach
        void openDuringSotw() {
            claims.setLockWindow(() -> sotw);
            give(wizards, chunk(0, 0));
        }

        @Test
        void aClaimCannotBeLockedOutsideSotw() {
            assertEquals(ClaimMessages.LOCK_NOT_NOW, claims.toggleLock(wizards, alice).getMessageKey());
            assertFalse(claims.isLocked(wizards.getId()));
        }

        @Test
        void whileLockedOnlyMembersMayStandInIt() {
            sotw = true;
            assertTrue(claims.toggleLock(wizards, alice).isSuccess());

            assertTrue(claims.lockedAgainst(chunk(0, 0), bob).isPresent(), "an outsider is kept out");
            assertTrue(claims.lockedAgainst(chunk(0, 0), alice).isEmpty(), "a member walks in");
            assertTrue(claims.lockedAgainst(chunk(5, 5), bob).isEmpty(), "and the wilderness is still open");
        }

        /** "Everyone except team members": an alliance does not open a locked claim. */
        @Test
        void alliesAreKeptOutToo() {
            sotw = true;
            teamManager.ally(wizards, alice, warlocks);
            teamManager.ally(warlocks, bob, wizards);
            assertTrue(wizards.getAllies().contains(warlocks.getId()), "setup: the two teams are allied");
            claims.toggleLock(wizards, alice);
            assertTrue(claims.lockedAgainst(chunk(0, 0), bob).isPresent());
        }

        @Test
        void onlyTheConfiguredRoleMayLock() {
            sotw = true;
            UUID carol = UUID.randomUUID();
            teamManager.join(carol, wizards, true);
            assertEquals(ClaimMessages.INSUFFICIENT_ROLE, claims.toggleLock(wizards, carol).getMessageKey());
            assertFalse(claims.isLocked(wizards.getId()));
        }

        @Test
        void aSecondToggleOpensItAgain() {
            sotw = true;
            claims.toggleLock(wizards, alice);
            assertEquals(ClaimMessages.LOCK_OFF, claims.toggleLock(wizards, alice).getMessageKey());
            assertTrue(claims.lockedAgainst(chunk(0, 0), bob).isEmpty());
        }

        /** When SOTW ends every lock lapses, and the next SOTW starts with every claim open. */
        @Test
        void theEndOfSotwReleasesEveryLock() {
            sotw = true;
            claims.toggleLock(wizards, alice);
            sotw = false;
            assertTrue(claims.lockedAgainst(chunk(0, 0), bob).isEmpty());
            sotw = true;
            assertFalse(claims.isLocked(wizards.getId()), "a lock does not come back with the next SOTW");
        }
    }

    // ------------------------------------------------------------------

    @Nested
    class Reclaim {

        @Test
        void aRaidableTeamKeepsOwnershipOfItsLand() {
            give(wizards, chunk(0, 0));
            raidableTeams.add(wizards.getId());

            assertEquals(wizards.getId(), claims.getOwnerId(chunk(0, 0)).orElseThrow(),
                    "raiding must never change who owns the chunk");
            assertEquals(1, claims.getClaimCount(wizards.getId()));
        }

        @Test
        void anotherTeamCannotClaimRaidableLand() {
            give(wizards, chunk(0, 0));
            raidableTeams.add(wizards.getId());

            TeamResult result = claims.claim(warlocks, null, List.of(chunk(0, 0)));

            assertTrue(result.isFailure(), "over-claiming must be impossible, even during a raid");
            assertEquals(ClaimMessages.CLAIM_ALREADY_OWNED, result.getMessageKey());
            assertEquals(wizards.getId(), claims.getOwnerId(chunk(0, 0)).orElseThrow());
        }

        @Test
        void protectionLiftsWhileRaidableAndComesBackOnItsOwn() {
            give(wizards, chunk(0, 0));

            assertEquals(ProtectionResult.DENIED_CLAIMED, claims.checkProtection(warlocks, chunk(0, 0)));

            raidableTeams.add(wizards.getId());
            assertEquals(ProtectionResult.ALLOWED_RAID, claims.checkProtection(warlocks, chunk(0, 0)));

            // DTR regenerates above zero: nothing is re-claimed, protection just returns.
            raidableTeams.remove(wizards.getId());
            assertEquals(ProtectionResult.DENIED_CLAIMED, claims.checkProtection(warlocks, chunk(0, 0)));
            assertEquals(wizards.getId(), claims.getOwnerId(chunk(0, 0)).orElseThrow());
        }

        @Test
        void raidBuildingCanBeDisabledEntirely() {
            reconfigure(withProtection(new ClaimSettings.ProtectionRules(false, false, true, true)));
            give(wizards, chunk(0, 0));
            raidableTeams.add(wizards.getId());

            assertEquals(ProtectionResult.DENIED_CLAIMED, claims.checkProtection(warlocks, chunk(0, 0)));
        }

        @Test
        void withoutADtrModuleNothingIsEverRaidable() {
            ClaimManager fresh = new ClaimManager(() -> settings, teamManager, ClaimStore.NO_OP, now::get);
            fresh.claim(wizards, null, List.of(chunk(5, 5)));

            assertFalse(fresh.getRaidabilityPolicy().isRaidable(wizards.getId()));
            assertEquals(ProtectionResult.DENIED_CLAIMED, fresh.checkProtection(warlocks, chunk(5, 5)));
        }
    }

    /**
     * Server land - spawn, the warzone, roads, event grounds - is drawn by staff.
     * The allowance and placement rules exist so players share the map; they do not
     * apply to it. Over-claiming still does.
     */
    @Nested
    class ServerLand {

        private Team spawn;

        @BeforeEach
        void createSpawn() {
            spawn = teamManager.createSystemTeam("Spawn").getTeam().orElseThrow();
        }

        @Test
        void serverLandHasNoAllowance() {
            // A system team has no members, so the member-scaled allowance would hold
            // it to the base - sixteen chunks for a whole warzone.
            List<ChunkPosition> area = new ArrayList<>();
            for (int x = 0; x < 5; x++) {
                for (int z = 0; z < 5; z++) {
                    area.add(chunk(x, z));
                }
            }

            assertTrue(claims.claim(spawn, null, area).isSuccess());
            assertEquals(25, claims.getClaimCount(spawn.getId()));
            assertEquals(0, claims.getMaxClaims(spawn), "0 means unlimited");
        }

        @Test
        void serverLandNeedNotBeConnected() {
            give(spawn, chunk(0, 0));
            assertTrue(claims.claim(spawn, null, List.of(chunk(40, 40))).isSuccess(),
                    "a road is not connected to spawn by definition");
        }

        @Test
        void serverLandMayBorderAPlayerTeam() {
            give(wizards, chunk(10, 10));
            assertTrue(claims.claim(spawn, null, List.of(chunk(11, 10))).isSuccess(),
                    "the buffer is how players keep apart, not a rule for the server");
        }

        @Test
        void serverLandStillCannotTakeAnybodysChunk() {
            give(wizards, chunk(10, 10));
            TeamResult result = claims.claim(spawn, null, List.of(chunk(10, 10)));
            assertEquals(ClaimMessages.CLAIM_ALREADY_OWNED, result.getMessageKey());
            assertEquals(wizards.getId(), claims.getOwnerId(chunk(10, 10)).orElseThrow());
        }

        @Test
        void aPlayerTeamClaimedForByStaffKeepsItsPlacementRules() {
            give(wizards, chunk(0, 0));
            TeamResult result = claims.claim(wizards, null, List.of(chunk(20, 20)));
            assertEquals(ClaimMessages.CLAIM_NOT_CONNECTED, result.getMessageKey(),
                    "the exemption is for server land, not for anything staff types");
        }
    }

    /**
     * The warzone radius of claims.yml, beside server teams. The most specific rule
     * wins: claimed land follows its owner, the warzone governs only land nobody
     * owns, and a region another system governs is left to it.
     */
    @Nested
    class Warzone {

        /**
         * 100 blocks around 0,0 in "world": blocks -100..100, rounded outwards to
         * chunks -7..6 (chunk 6 is blocks 96..111, chunk -7 is -112..-97).
         */
        private final ClaimSettings.WarzoneRules around0 = new ClaimSettings.WarzoneRules("&cWarzone", false,
                Map.of("world", new ClaimSettings.WarzoneRules.Area(0, 0, 100)));

        @BeforeEach
        void configure() {
            reconfigure(withWarzone(around0));
        }

        @Test
        void theRadiusIsRoundedOutwardsToWholeChunks() {
            assertTrue(around0.covers(chunk(6, 0)), "blocks 96..111 reach block 100");
            assertFalse(around0.covers(chunk(7, 0)));
            assertTrue(around0.covers(chunk(-7, -7)), "blocks -112..-97 reach block -100");
            assertFalse(around0.covers(chunk(-8, 0)));
            assertFalse(around0.covers(new ChunkPosition("world_nether", 0, 0)),
                    "a world with no entry has no warzone");
        }

        @Test
        void noPlayerTeamCanClaimTheWarzoneEvenThroughStaff() {
            assertEquals(ClaimMessages.CLAIM_WARZONE,
                    claims.claim(wizards, alice, List.of(chunk(3, 3))).getMessageKey());
            assertEquals(ClaimMessages.CLAIM_WARZONE,
                    claims.claim(wizards, null, List.of(chunk(3, 3))).getMessageKey());
            assertTrue(claims.claim(wizards, null, List.of(chunk(7, 0))).isSuccess(),
                    "one chunk past the border, claiming works as usual");
        }

        @Test
        void aServerTeamMayClaimInsideIt() {
            Team spawn = teamManager.createSystemTeam("Spawn").getTeam().orElseThrow();
            assertTrue(claims.claim(spawn, null, List.of(chunk(0, 0), chunk(0, 1))).isSuccess(),
                    "spawn sits at the warzone's centre");
        }

        @Test
        void nobodyBuildsOnUnclaimedWarzone() {
            assertEquals(ProtectionResult.DENIED_WARZONE, claims.checkBuild(warlocks, "world", 5, 64, 5));
            assertEquals(ProtectionResult.DENIED_WARZONE, claims.checkBuild(null, "world", -100, 64, 100),
                    "a teamless player is refused too, bounds included");
            assertEquals(ProtectionResult.ALLOWED, claims.checkBuild(warlocks, "world", 112, 64, 0),
                    "past the rounded border is ordinary wilderness");
        }

        @Test
        void buildingThereCanBeAllowed() {
            reconfigure(withWarzone(new ClaimSettings.WarzoneRules("&cWarzone", true, around0.areas())));
            assertEquals(ProtectionResult.ALLOWED, claims.checkBuild(warlocks, "world", 5, 64, 5));
            assertFalse(claims.isExplosionProtected("world", 0, 64, 0));
        }

        @Test
        void interactingIsNotBuilding() {
            // Doors, chests, buttons: the warzone is public land, not somebody's property.
            assertEquals(ProtectionResult.ALLOWED, claims.checkProtection(warlocks, chunk(0, 0)));
        }

        @Test
        void claimedLandInsideFollowsItsOwner() {
            Team spawn = teamManager.createSystemTeam("Spawn").getTeam().orElseThrow();
            give(spawn, chunk(0, 0));
            // A player team that held land here before the warzone was set up keeps it.
            reconfigure(withWarzone(ClaimSettings.WarzoneRules.none()));
            give(wizards, chunk(3, 3));
            reconfigure(withWarzone(around0));

            assertEquals(ProtectionResult.DENIED_SYSTEM, claims.checkBuild(warlocks, "world", 5, 64, 5));
            assertEquals(ProtectionResult.ALLOWED, claims.checkBuild(wizards, "world", 50, 64, 50),
                    "a team builds in its own territory, warzone or not");
            assertEquals(ProtectionResult.DENIED_CLAIMED, claims.checkBuild(warlocks, "world", 50, 64, 50));
            assertFalse(claims.isWarzone(chunk(3, 3)), "owned land is not warzone");
            assertTrue(claims.isWarzone(chunk(4, 4)));
        }

        @Test
        void explosionsCannotTakeTheWarzone() {
            assertTrue(claims.isExplosionProtected("world", 16, 64, 16));
            assertFalse(claims.isExplosionProtected("world", 320, 64, 320), "wilderness still blows up");
        }

        @Test
        void aRegionAnotherSystemGovernsIsLeftToIt() {
            // A Mountain inside the warzone: its blocks follow the Mountain's rules,
            // not the warzone's "no building", or its resources could never be mined.
            claims.setReservedRegionPolicy(new ReservedRegionPolicy() {
                @Override
                public java.util.Optional<String> reservedRegionAt(ChunkPosition chunk) {
                    return java.util.Optional.empty();
                }

                @Override
                public boolean isInReservedRegion(String world, int x, int y, int z) {
                    return x == 5 && y == 64 && z == 5;
                }
            });

            assertEquals(ProtectionResult.ALLOWED, claims.checkBuild(warlocks, "world", 5, 64, 5));
            assertEquals(ProtectionResult.DENIED_WARZONE, claims.checkBuild(warlocks, "world", 6, 64, 5),
                    "only the region's own blocks are handed over, not the rest of the chunk");
            assertFalse(claims.isExplosionProtected("world", 5, 64, 5),
                    "explosions are handed over too: the region's own rules decide them");
            assertTrue(claims.isExplosionProtected("world", 6, 64, 5));
        }

        @Test
        void aTeamNamedLikeTheWarzoneIsStillAnotherTerritory() {
            // The display name is free text, and a server team may well carry it.
            reconfigure(withWarzone(new ClaimSettings.WarzoneRules("Warzone", false, around0.areas())));
            Team road = teamManager.createSystemTeam("Warzone").getTeam().orElseThrow();
            give(road, chunk(0, 0));

            assertFalse(claims.isSameTerritory(chunk(0, 0), chunk(0, 1)),
                    "from the team's chunk to unclaimed warzone is a border, whatever the names");
            assertTrue(claims.isSameTerritory(chunk(1, 1), chunk(2, 2)), "warzone to warzone");
            assertFalse(claims.isSameTerritory(chunk(6, 0), chunk(7, 0)), "warzone to wilderness");
            assertTrue(claims.isSameTerritory(chunk(20, 20), chunk(21, 21)), "wilderness to wilderness");
            assertTrue(claims.isSameTerritory(chunk(0, 0), chunk(0, 0)));
        }
    }

    @Nested
    class Protection {

        @Test
        void wildernessIsOpenAndOwnTerritoryIsAlwaysAllowed() {
            give(wizards, chunk(0, 0));

            assertEquals(ProtectionResult.ALLOWED, claims.checkProtection(warlocks, chunk(9, 9)));
            assertEquals(ProtectionResult.ALLOWED, claims.checkProtection(wizards, chunk(0, 0)));
            assertEquals(ProtectionResult.ALLOWED, claims.checkProtection(alice, chunk(0, 0)));
        }

        @Test
        void aTeamlessPlayerIsBlockedByAClaim() {
            give(wizards, chunk(0, 0));

            assertEquals(ProtectionResult.DENIED_CLAIMED,
                    claims.checkProtection(UUID.randomUUID(), chunk(0, 0)));
        }

        @Test
        void systemTeamLandIsNeverBuildableAndNotEvenRaidable() {
            Team spawn = teamManager.createSystemTeam("Spawn").getTeam().orElseThrow();
            give(spawn, chunk(0, 0));
            raidableTeams.add(spawn.getId());

            assertEquals(ProtectionResult.DENIED_SYSTEM, claims.checkProtection(warlocks, chunk(0, 0)));
        }

        @Test
        void alliesAreBlockedByDefaultAndAllowedWhenConfigured() {
            give(wizards, chunk(0, 0));
            teamManager.ally(wizards, alice, warlocks);
            teamManager.ally(warlocks, bob, wizards);

            assertEquals(ProtectionResult.DENIED_ALLY, claims.checkProtection(warlocks, chunk(0, 0)));

            reconfigure(withProtection(new ClaimSettings.ProtectionRules(true, true, true, true)));
            assertEquals(ProtectionResult.ALLOWED, claims.checkProtection(warlocks, chunk(0, 0)));
        }

        @Test
        void explosionsCannotDoWhatAPlayerWasRefused() {
            give(wizards, chunk(0, 0));

            assertTrue(claims.isExplosionProtected("world", 0, 64, 0),
                    "otherwise a player refused a block break just throws TNT instead");
            assertFalse(claims.isExplosionProtected("world", 144, 64, 144), "wilderness still blows up");
        }

        @Test
        void aRaidableTeamsTerritoryStillBlowsUp() {
            // An explosion has no team, so the question is asked as a stranger would
            // ask it - and a stranger may pillage a raidable team. Stopping TNT here
            // would make raiding harder than mining by hand, which is backwards.
            give(wizards, chunk(0, 0));
            raidableTeams.add(wizards.getId());

            assertFalse(claims.isExplosionProtected("world", 0, 64, 0));
        }

        @Test
        void explosionProtectionCanBeTurnedOff() {
            reconfigure(withProtection(new ClaimSettings.ProtectionRules(false, true, true, false)));
            give(wizards, chunk(0, 0));

            assertFalse(claims.isExplosionProtected("world", 0, 64, 0));
        }

        @Test
        void landOwnedByAVanishedTeamFallsBackToWilderness() {
            give(wizards, chunk(0, 0));
            teamManager.disband(wizards, null);

            // The claim rows outlived the team; the land must open up rather than
            // stay locked forever behind an owner nobody can raid.
            assertEquals(ProtectionResult.ALLOWED, claims.checkProtection(warlocks, chunk(0, 0)));
            assertTrue(claims.getOwnerId(chunk(0, 0)).isEmpty());
        }
    }

    /**
     * What the world does across a border on its own - pistons, liquids, fire,
     * dispensers. The project owner's rule, 15/09/2026: what comes from another
     * territory is judged as if its owner did it.
     */
    @Nested
    class AcrossBorders {

        /** 100 blocks around 0,0: chunks -7..6 are warzone, chunk 7 (blocks 112..127) is not. */
        private final ClaimSettings.WarzoneRules around0 = new ClaimSettings.WarzoneRules("&cWarzone", false,
                Map.of("world", new ClaimSettings.WarzoneRules.Area(0, 0, 100)));

        @Test
        void wildernessCannotReachAProtectedTeam() {
            give(wizards, chunk(0, 0));

            assertFalse(claims.mayReach("world", -1, 0, 0, 64, 0),
                    "a piston outside must not pull a wall a teamless player could not break");
            assertTrue(claims.mayReach("world", 0, 0, -1, 64, 0), "the team's own reaches out into the wilderness");
        }

        @Test
        void aRaidableTeamIsReachedAsItIsBuiltIn() {
            give(wizards, chunk(0, 0));
            raidableTeams.add(wizards.getId());

            assertTrue(claims.mayReach("world", -1, 0, 0, 64, 0));
        }

        @Test
        void aTeamsMachinesWorkAcrossItsOwnChunks() {
            give(wizards, chunk(0, 0), chunk(1, 0));

            assertTrue(claims.mayReach("world", 15, 0, 16, 64, 0));
            assertTrue(claims.mayReach("world", 3, 3, 4, 64, 3), "within one chunk everything flows");
        }

        @Test
        void oneTeamCannotReachAnother() {
            give(wizards, chunk(0, 0));
            give(warlocks, chunk(10, 0));

            assertFalse(claims.mayReach("world", 160, 0, 15, 64, 0));
            assertFalse(claims.mayReach("world", 15, 0, 160, 64, 0));
        }

        @Test
        void alliesFollowTheAllyBuildSetting() {
            give(wizards, chunk(0, 0));
            give(warlocks, chunk(10, 0));
            teamManager.ally(wizards, alice, warlocks);
            teamManager.ally(warlocks, bob, wizards);

            assertFalse(claims.mayReach("world", 160, 0, 15, 64, 0));
            reconfigure(withProtection(new ClaimSettings.ProtectionRules(true, true, true, true)));
            assertTrue(claims.mayReach("world", 160, 0, 15, 64, 0), "an ally who may build there may reach it");
        }

        @Test
        void theWarzoneIsReachedOnlyFromWithin() {
            reconfigure(withWarzone(around0));

            assertFalse(claims.mayReach("world", 112, 0, 111, 64, 0), "nobody may build on it from the wilderness");
            assertTrue(claims.mayReach("world", 111, 0, 112, 64, 0), "anybody may build in the wilderness");
            assertTrue(claims.mayReach("world", 0, 0, 16, 64, 0), "warzone to warzone");
        }

        @Test
        void serverLandReachesUnclaimedAndServerLandButNotAPlayersBase() {
            reconfigure(withWarzone(around0));
            Team spawn = teamManager.createSystemTeam("Spawn").getTeam().orElseThrow();
            Team road = teamManager.createSystemTeam("Road").getTeam().orElseThrow();
            give(wizards, chunk(20, 0));
            // Server land may border a player team; the other way round, the buffer refuses.
            give(spawn, chunk(0, 0), chunk(19, 0));
            give(road, chunk(0, 1));

            assertTrue(claims.mayReach("world", 0, 0, 16, 64, 0), "a fountain on spawn's edge runs into the warzone");
            assertTrue(claims.mayReach("world", 0, 15, 0, 64, 16), "spawn to a road: both are the server's");
            assertFalse(claims.mayReach("world", 16, 0, 0, 64, 0), "the warzone does not reach into spawn");
            assertFalse(claims.mayReach("world", 319, 0, 320, 64, 0), "a player's base is judged as for anybody");

            raidableTeams.add(wizards.getId());
            assertTrue(claims.mayReach("world", 319, 0, 320, 64, 0));
        }

        @Test
        void aRegionAnotherSystemGovernsIsLeftToIt() {
            reconfigure(withWarzone(around0));
            claims.setReservedRegionPolicy(new ReservedRegionPolicy() {
                @Override
                public java.util.Optional<String> reservedRegionAt(ChunkPosition chunk) {
                    return java.util.Optional.empty();
                }

                @Override
                public boolean isInReservedRegion(String world, int x, int y, int z) {
                    return x == 111 && y == 64 && z == 0;
                }
            });

            assertTrue(claims.mayReach("world", 112, 0, 111, 64, 0), "the Mountain's own rules decide its edge");
            assertFalse(claims.mayReach("world", 112, 0, 111, 65, 0));
        }

        @Test
        void nothingIsStoppedWhileTheModuleIsOff() {
            give(wizards, chunk(0, 0));
            reconfigure(withEnabled(false));

            assertTrue(claims.mayReach("world", -1, 0, 0, 64, 0));
        }
    }

    /**
     * EOTW closes claiming through {@link ClaimingPolicy}. Players are refused with
     * the reason the policy gives; staff drawing server land are not.
     */
    @Nested
    class ClaimingClosed {

        @BeforeEach
        void close() {
            claims.setClaimingPolicy(() -> java.util.Optional.of("phase.eotw.no-claims"));
        }

        @Test
        void aPlayerClaimIsRefusedWithThePolicysReason() {
            TeamResult result = claims.claim(wizards, alice, List.of(chunk(10, 10)));

            assertTrue(result.isFailure());
            assertEquals("phase.eotw.no-claims", result.getMessageKey());
            assertEquals(0, claims.getClaimCount(wizards.getId()));
        }

        @Test
        void staffStillClaimForServerTeams() {
            Team road = teamManager.createSystemTeam("Road").getTeam().orElseThrow();
            assertTrue(claims.claim(road, null, List.of(chunk(10, 10))).isSuccess());
        }

        @Test
        void landAlreadyHeldIsUntouched() {
            give(wizards, chunk(0, 0));
            claims.setClaimingPolicy(() -> java.util.Optional.of("phase.eotw.no-claims"));
            assertEquals(wizards.getId(), claims.getOwnerId(chunk(0, 0)).orElseThrow());
        }

        @Test
        void reopeningLetsPlayersClaimAgain() {
            claims.setClaimingPolicy(ClaimingPolicy.OPEN);
            assertTrue(claims.claim(wizards, alice, List.of(chunk(10, 10))).isSuccess());
        }
    }

    /**
     * {@code enabled: false} turns off claiming and protection together, as
     * claims.yml says. Only claiming used to be off: territory stayed protected
     * while the operator had been told otherwise. The last test turns the module
     * back on over the same land, which is what proves the others pass because of
     * the flag rather than because nothing was protected to begin with.
     */
    @Nested
    class Disabled {

        private Team spawn;

        @BeforeEach
        void configure() {
            spawn = teamManager.createSystemTeam("Spawn").getTeam().orElseThrow();
            give(spawn, chunk(0, 0));
            give(wizards, chunk(20, 20));
            reconfigure(withEnabled(false));
        }

        @Test
        void nobodyClaimsNotEvenStaff() {
            assertFalse(claims.isEnforced());
            assertEquals(ClaimMessages.CLAIM_DISABLED,
                    claims.claim(wizards, alice, List.of(chunk(21, 20))).getMessageKey());
            assertEquals(ClaimMessages.CLAIM_DISABLED,
                    claims.claim(spawn, null, List.of(chunk(0, 1))).getMessageKey());
        }

        @Test
        void noTerritoryIsProtectedPlayerOrServer() {
            assertEquals(ProtectionResult.ALLOWED, claims.checkProtection(warlocks, chunk(20, 20)));
            assertEquals(ProtectionResult.ALLOWED, claims.checkBuild(warlocks, "world", 320, 64, 320));
            assertEquals(ProtectionResult.ALLOWED, claims.checkProtection(warlocks, chunk(0, 0)),
                    "spawn included: the switch covers all territory");
            assertEquals(ProtectionResult.ALLOWED, claims.checkBuild(null, "world", 0, 64, 0));
        }

        @Test
        void explosionsAreNotFilteredEither() {
            assertFalse(claims.isExplosionProtected("world", 320, 64, 320));
            assertFalse(claims.isExplosionProtected("world", 0, 64, 0));
        }

        @Test
        void theWarzoneGovernsNothing() {
            reconfigure(withWarzone(new ClaimSettings.WarzoneRules("&cWarzone", false,
                    Map.of("world", new ClaimSettings.WarzoneRules.Area(0, 0, 100)))));

            assertEquals(ProtectionResult.ALLOWED, claims.checkBuild(warlocks, "world", 50, 64, 50));
            assertFalse(claims.isExplosionProtected("world", 48, 64, 48));
            assertFalse(claims.isWarzone(chunk(3, 3)), "no rule applies there, so it reads as wilderness");

            reconfigure(withEnabled(true));
            assertEquals(ProtectionResult.DENIED_WARZONE, claims.checkBuild(warlocks, "world", 50, 64, 50));
            assertTrue(claims.isWarzone(chunk(3, 3)));
        }

        @Test
        void ownershipIsKeptAndEveryRuleComesBackWhenTurnedOn() {
            assertEquals(wizards.getId(), claims.getOwnerId(chunk(20, 20)).orElseThrow());
            assertEquals(spawn.getId(), claims.getOwnerId(chunk(0, 0)).orElseThrow());

            reconfigure(withEnabled(true));

            assertEquals(ProtectionResult.DENIED_CLAIMED, claims.checkProtection(warlocks, chunk(20, 20)));
            assertEquals(ProtectionResult.DENIED_SYSTEM, claims.checkProtection(warlocks, chunk(0, 0)));
            assertTrue(claims.isExplosionProtected("world", 320, 64, 320));
            assertTrue(claims.isExplosionProtected("world", 0, 64, 0));
        }
    }

    @Nested
    class Claiming {

        @Test
        void claimingIndexesBothWays() {
            TeamResult result = claims.claim(wizards, alice, List.of(chunk(0, 0), chunk(1, 0)));

            assertTrue(result.isSuccess());
            assertEquals(2, claims.getClaimCount(wizards.getId()));
            assertEquals(Set.of(chunk(0, 0), chunk(1, 0)), claims.getClaims(wizards.getId()));
            assertEquals(wizards.getId(), claims.getOwnerId(chunk(1, 0)).orElseThrow());
            assertTrue(claims.isClaimed(chunk(0, 0)));
        }

        @Test
        void claimingIsAllOrNothing() {
            give(warlocks, chunk(1, 0));

            TeamResult result = claims.claim(wizards, null, List.of(chunk(0, 0), chunk(1, 0)));

            assertTrue(result.isFailure());
            assertEquals(0, claims.getClaimCount(wizards.getId()),
                    "the valid chunk of a rejected batch must not be claimed either");
        }

        @Test
        void reclaimingYourOwnChunkIsReportedDistinctly() {
            give(wizards, chunk(0, 0));

            assertEquals(ClaimMessages.CLAIM_ALREADY_YOURS,
                    claims.claim(wizards, null, List.of(chunk(0, 0))).getMessageKey());
        }

        @Test
        void anEmptySelectionIsRejected() {
            assertEquals(ClaimMessages.CLAIM_NOTHING_SELECTED,
                    claims.claim(wizards, alice, List.of()).getMessageKey());
        }

        @Test
        void claimingCanBeDisabledServerWide() {
            reconfigure(withEnabled(false));

            assertEquals(ClaimMessages.CLAIM_DISABLED,
                    claims.claim(wizards, alice, List.of(chunk(0, 0))).getMessageKey());
        }

        @Test
        void onlyWhitelistedWorldsAreClaimable() {
            reconfigure(withWorlds(Set.of("world")));

            assertTrue(claims.claim(wizards, null, List.of(chunk(0, 0))).isSuccess());
            assertEquals(ClaimMessages.CLAIM_WORLD_DISABLED,
                    claims.claim(wizards, null, List.of(new ChunkPosition("world_nether", 0, 0)))
                            .getMessageKey());
        }

        @Test
        void aPlayerCannotClaimForASystemTeamButStaffCan() {
            Team spawn = teamManager.createSystemTeam("Spawn").getTeam().orElseThrow();

            assertEquals(ClaimMessages.CLAIM_SYSTEM_TEAM,
                    claims.claim(spawn, alice, List.of(chunk(0, 0))).getMessageKey());
            assertTrue(claims.claim(spawn, null, List.of(chunk(0, 0))).isSuccess());
        }
    }

    @Nested
    class Limits {

        @Test
        void theAllowanceScalesWithMemberCount() {
            reconfigure(withLimits(new ClaimSettings.LimitRules(10, 5, 0, 64)));

            assertEquals(15, claims.getMaxClaims(wizards));

            teamManager.join(UUID.randomUUID(), wizards, true);
            assertEquals(20, claims.getMaxClaims(wizards));
        }

        @Test
        void theHardMaximumCapsTheScaling() {
            reconfigure(withLimits(new ClaimSettings.LimitRules(10, 5, 12, 64)));

            assertEquals(12, claims.getMaxClaims(wizards));
        }

        @Test
        void perMemberScalingCanBeTurnedOff() {
            reconfigure(withLimits(new ClaimSettings.LimitRules(8, 0, 0, 64)));

            assertEquals(8, claims.getMaxClaims(wizards));
        }

        @Test
        void claimingStopsAtTheAllowance() {
            reconfigure(withLimits(new ClaimSettings.LimitRules(2, 0, 0, 64)));
            reconfigure(withPlacement(new ClaimSettings.PlacementRules(false, 0, true)));

            assertTrue(claims.claim(wizards, null, List.of(chunk(0, 0), chunk(1, 0))).isSuccess());
            assertEquals(ClaimMessages.CLAIM_LIMIT_REACHED,
                    claims.claim(wizards, null, List.of(chunk(2, 0))).getMessageKey());
        }

        @Test
        void aSingleCommandCannotSwallowTheMapButStaffAreExempt() {
            reconfigure(withLimits(new ClaimSettings.LimitRules(0, 0, 0, 2)));
            reconfigure(withPlacement(new ClaimSettings.PlacementRules(false, 0, true)));
            List<ChunkPosition> many = List.of(chunk(0, 0), chunk(1, 0), chunk(2, 0));

            assertEquals(ClaimMessages.CLAIM_TOO_MANY_AT_ONCE,
                    claims.claim(wizards, alice, many).getMessageKey());
            assertTrue(claims.claim(wizards, null, many).isSuccess());
        }
    }

    @Nested
    class Placement {

        @Test
        void territoryMustStayConnectedWithinAWorld() {
            reconfigure(withPlacement(new ClaimSettings.PlacementRules(true, 0, true)));
            give(wizards, chunk(0, 0));

            assertEquals(ClaimMessages.CLAIM_NOT_CONNECTED,
                    claims.claim(wizards, null, List.of(chunk(5, 5))).getMessageKey());
            assertTrue(claims.claim(wizards, null, List.of(chunk(1, 0))).isSuccess());
        }

        @Test
        void aBatchConnectsThroughItsOwnChunks() {
            reconfigure(withPlacement(new ClaimSettings.PlacementRules(true, 0, true)));
            give(wizards, chunk(0, 0));

            // Only (1,0) touches the border; (2,0) reaches it through (1,0).
            assertTrue(claims.claim(wizards, null, List.of(chunk(1, 0), chunk(2, 0))).isSuccess());
        }

        @Test
        void aTeamMayOpenATerritoryInAWorldItDoesNotHoldYet() {
            reconfigure(withPlacement(new ClaimSettings.PlacementRules(true, 0, true)));
            give(wizards, chunk(0, 0));

            assertTrue(claims.claim(wizards, null,
                    List.of(new ChunkPosition("world_nether", 40, 40))).isSuccess());
        }

        @Test
        void theBufferKeepsTeamsApart() {
            reconfigure(withPlacement(new ClaimSettings.PlacementRules(false, 3, true)));
            give(warlocks, chunk(0, 0));

            assertEquals(ClaimMessages.CLAIM_TOO_CLOSE,
                    claims.claim(wizards, null, List.of(chunk(2, 0))).getMessageKey());
            assertTrue(claims.claim(wizards, null, List.of(chunk(3, 0))).isSuccess());
        }

        @Test
        void theBufferIgnoresYourOwnClaims() {
            reconfigure(withPlacement(new ClaimSettings.PlacementRules(false, 3, true)));
            give(wizards, chunk(0, 0));

            assertTrue(claims.claim(wizards, null, List.of(chunk(1, 0))).isSuccess());
        }

        @Test
        void theBufferDoesNotReachAcrossWorlds() {
            reconfigure(withPlacement(new ClaimSettings.PlacementRules(false, 5, true)));
            give(warlocks, chunk(0, 0));

            assertTrue(claims.claim(wizards, null,
                    List.of(new ChunkPosition("world_nether", 0, 0))).isSuccess());
        }
    }

    @Nested
    class Unclaiming {

        @Test
        void unclaimingReleasesTheChunk() {
            give(wizards, chunk(0, 0));

            assertTrue(claims.unclaim(wizards, null, chunk(0, 0)).isSuccess());
            assertTrue(claims.getOwnerId(chunk(0, 0)).isEmpty());
            assertEquals(0, claims.getClaimCount(wizards.getId()));
        }

        @Test
        void youCannotUnclaimSomebodyElsesLand() {
            give(warlocks, chunk(0, 0));

            assertEquals(ClaimMessages.UNCLAIM_NOT_YOURS,
                    claims.unclaim(wizards, null, chunk(0, 0)).getMessageKey());
        }

        @Test
        void unclaimingWildernessFails() {
            assertEquals(ClaimMessages.UNCLAIM_NOT_CLAIMED,
                    claims.unclaim(wizards, null, chunk(0, 0)).getMessageKey());
        }

        @Test
        void splittingYourTerritoryInTwoIsRefusedUnlessAllowed() {
            reconfigure(withPlacement(new ClaimSettings.PlacementRules(false, 0, false)));
            give(wizards, chunk(0, 0), chunk(1, 0), chunk(2, 0));

            assertEquals(ClaimMessages.UNCLAIM_WOULD_DISCONNECT,
                    claims.unclaim(wizards, alice, chunk(1, 0)).getMessageKey());
            assertTrue(claims.unclaim(wizards, alice, chunk(2, 0)).isSuccess(),
                    "removing an edge chunk keeps the territory whole");
        }

        @Test
        void unclaimAllEmptiesTheTerritory() {
            give(wizards, chunk(0, 0), chunk(1, 0));

            TeamResult result = claims.unclaimAll(wizards, null);

            assertTrue(result.isSuccess());
            assertEquals("2", result.getPlaceholders().get("count"));
            assertEquals(0, claims.getClaimCount(wizards.getId()));
            assertTrue(claims.getOwnerId(chunk(0, 0)).isEmpty());
        }
    }

    @Nested
    class Homes {

        @Test
        void aHomeMustSitInsideYourOwnTerritory() {
            give(wizards, chunk(0, 0));

            assertEquals(ClaimMessages.HOME_NOT_IN_TERRITORY,
                    claims.setHome(wizards, null, HomeType.HQ,
                            WorldPosition.of("world", 500, 64, 500)).getMessageKey());

            assertTrue(claims.setHome(wizards, null, HomeType.HQ,
                    WorldPosition.of("world", 8, 64, 8)).isSuccess());
            assertEquals(HomeType.HQ, claims.getHome(wizards.getId(), HomeType.HQ).orElseThrow().type());
        }

        @Test
        void theInsideTerritoryRuleCanBeRelaxed() {
            reconfigure(withHomes(new ClaimSettings.HomeRules(true, false, 0L)));

            assertTrue(claims.setHome(wizards, null, HomeType.HQ,
                    WorldPosition.of("world", 500, 64, 500)).isSuccess());
        }

        @Test
        void hqAndSecondaryBaseAreStoredSeparately() {
            reconfigure(withHomes(new ClaimSettings.HomeRules(true, false, 0L)));
            claims.setHome(wizards, null, HomeType.HQ, WorldPosition.of("world", 1, 64, 1));
            claims.setHome(wizards, null, HomeType.BASE, WorldPosition.of("world", 2, 64, 2));

            assertEquals(2, claims.getHomes(wizards.getId()).size());
            assertEquals(1.0, claims.getHome(wizards.getId(), HomeType.HQ).orElseThrow().position().x());
            assertEquals(2.0, claims.getHome(wizards.getId(), HomeType.BASE).orElseThrow().position().x());
        }

        @Test
        void homesCanBeDisabled() {
            reconfigure(withHomes(new ClaimSettings.HomeRules(false, false, 0L)));

            assertEquals(ClaimMessages.HOME_DISABLED,
                    claims.setHome(wizards, null, HomeType.HQ,
                            WorldPosition.of("world", 0, 64, 0)).getMessageKey());
        }

        @Test
        void negativeCoordinatesLandInTheRightChunk() {
            give(wizards, new ChunkPosition("world", -1, -1));

            assertTrue(claims.setHome(wizards, null, HomeType.HQ,
                    WorldPosition.of("world", -8, 64, -8)).isSuccess(),
                    "block -8 belongs to chunk -1, not chunk 0");
        }
    }

    @Nested
    class Permissions {

        @Test
        void claimingNeedsTheConfiguredRank() {
            UUID carol = UUID.randomUUID();
            teamManager.join(carol, wizards, true);

            assertEquals(ClaimMessages.INSUFFICIENT_ROLE,
                    claims.claim(wizards, carol, List.of(chunk(0, 0))).getMessageKey());

            teamManager.promote(wizards, alice, carol);
            assertTrue(claims.claim(wizards, carol, List.of(chunk(0, 0))).isSuccess());
        }

        @Test
        void aNonMemberCannotActOnTheTeam() {
            assertEquals(TeamMessages.NOT_A_MEMBER,
                    claims.claim(wizards, bob, List.of(chunk(0, 0))).getMessageKey());
        }

        @Test
        void requiredRolesAreConfigurable() {
            Map<ClaimAction, TeamRole> roles = new EnumMap<>(settings.requiredRoles());
            roles.put(ClaimAction.CLAIM, TeamRole.MEMBER);
            reconfigure(withRoles(roles));
            UUID carol = UUID.randomUUID();
            teamManager.join(carol, wizards, true);

            assertTrue(claims.claim(wizards, carol, List.of(chunk(0, 0))).isSuccess());
        }
    }

    @Nested
    class Persistence {

        @Test
        void flushWritesChangedTeamsOnly() throws Exception {
            RecordingStore store = new RecordingStore();
            ClaimManager persisting = new ClaimManager(() -> settings, teamManager, store, now::get);

            persisting.claim(wizards, null, List.of(chunk(0, 0)));
            assertEquals(1, persisting.flush());
            assertEquals(List.of(wizards.getId()), store.savedClaims);

            store.savedClaims.clear();
            assertEquals(0, persisting.flush(), "nothing changed, nothing written");
        }

        @Test
        void releasingATeamQueuesADelete() throws Exception {
            RecordingStore store = new RecordingStore();
            ClaimManager persisting = new ClaimManager(() -> settings, teamManager, store, now::get);
            persisting.claim(wizards, null, List.of(chunk(0, 0)));
            persisting.flush();

            persisting.releaseAll(wizards.getId());
            persisting.flush();

            assertEquals(List.of(wizards.getId()), store.deletedTeams);
        }

        @Test
        void aFailedWriteKeepsTheTeamQueued() {
            RecordingStore store = new RecordingStore();
            store.failOnSave = true;
            ClaimManager persisting = new ClaimManager(() -> settings, teamManager, store, now::get);
            persisting.claim(wizards, null, List.of(chunk(0, 0)));

            assertThrows(IllegalStateException.class, persisting::flush);

            store.failOnSave = false;
            assertDoesNotThrowAndWrites(persisting);
        }

        private void assertDoesNotThrowAndWrites(ClaimManager persisting) {
            try {
                assertEquals(1, persisting.flush(), "the unwritten change must be retried");
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }

        @Test
        void loadAllRebuildsBothIndexes() throws Exception {
            RecordingStore store = new RecordingStore();
            store.preloadedClaims.add(new Claim(wizards.getId(), chunk(3, 4), 1L));
            store.preloadedHomes.add(new TeamHome(wizards.getId(), HomeType.HQ,
                    WorldPosition.of("world", 50, 64, 70), 1L));
            ClaimManager loading = new ClaimManager(() -> settings, teamManager, store, now::get);

            loading.loadAll();

            assertEquals(wizards.getId(), loading.getOwnerId(chunk(3, 4)).orElseThrow());
            assertEquals(Set.of(chunk(3, 4)), loading.getClaims(wizards.getId()));
            assertTrue(loading.getHome(wizards.getId(), HomeType.HQ).isPresent());
        }
    }

    @Test
    void disbandingATeamFreesItsTerritory() {
        give(wizards, chunk(0, 0), chunk(1, 0));

        assertEquals(2, claims.releaseAll(wizards.getId()));

        assertTrue(claims.getOwnerId(chunk(0, 0)).isEmpty());
        assertEquals(0, claims.getClaimCount(wizards.getId()));
        assertTrue(claims.getHomes(wizards.getId()).isEmpty());
    }

    @Test
    void configChangesApplyWithoutRecreatingTheManager() {
        reconfigure(withEnabled(false));
        assertTrue(claims.claim(wizards, alice, List.of(chunk(0, 0))).isFailure());

        reconfigure(withEnabled(true));
        assertTrue(claims.claim(wizards, alice, List.of(chunk(0, 0))).isSuccess(),
                "/hcf reload must be enough");
    }

    @Test
    void theManagerKeepsUsingTheSameTeamInstances() {
        give(wizards, chunk(0, 0));

        assertSame(wizards, claims.getOwner(chunk(0, 0)).orElseThrow());
    }

    // --- settings helpers -------------------------------------------------

    private void reconfigure(ClaimSettings replacement) {
        this.settings = replacement;
    }

    private ClaimSettings withEnabled(boolean enabled) {
        return new ClaimSettings(enabled, settings.limits(), settings.placement(), settings.protection(),
                settings.homes(), settings.claimableWorlds(), settings.requiredRoles(), settings.warzone(),
                settings.stuck());
    }

    private ClaimSettings withLimits(ClaimSettings.LimitRules limits) {
        return new ClaimSettings(settings.enabled(), limits, settings.placement(), settings.protection(),
                settings.homes(), settings.claimableWorlds(), settings.requiredRoles(), settings.warzone(),
                settings.stuck());
    }

    private ClaimSettings withPlacement(ClaimSettings.PlacementRules placement) {
        return new ClaimSettings(settings.enabled(), settings.limits(), placement, settings.protection(),
                settings.homes(), settings.claimableWorlds(), settings.requiredRoles(), settings.warzone(),
                settings.stuck());
    }

    private ClaimSettings withProtection(ClaimSettings.ProtectionRules protection) {
        return new ClaimSettings(settings.enabled(), settings.limits(), settings.placement(), protection,
                settings.homes(), settings.claimableWorlds(), settings.requiredRoles(), settings.warzone(),
                settings.stuck());
    }

    private ClaimSettings withHomes(ClaimSettings.HomeRules homes) {
        return new ClaimSettings(settings.enabled(), settings.limits(), settings.placement(),
                settings.protection(), homes, settings.claimableWorlds(), settings.requiredRoles(), settings.warzone(),
                settings.stuck());
    }

    private ClaimSettings withWorlds(Set<String> worlds) {
        return new ClaimSettings(settings.enabled(), settings.limits(), settings.placement(),
                settings.protection(), settings.homes(), worlds, settings.requiredRoles(), settings.warzone(),
                settings.stuck());
    }

    private ClaimSettings withWarzone(ClaimSettings.WarzoneRules warzone) {
        return new ClaimSettings(settings.enabled(), settings.limits(), settings.placement(),
                settings.protection(), settings.homes(), settings.claimableWorlds(), settings.requiredRoles(),
                warzone, settings.stuck());
    }

    private ClaimSettings withRoles(Map<ClaimAction, TeamRole> roles) {
        return new ClaimSettings(settings.enabled(), settings.limits(), settings.placement(),
                settings.protection(), settings.homes(), settings.claimableWorlds(), roles, settings.warzone(),
                settings.stuck());
    }

    // --- test double ------------------------------------------------------

    private static class RecordingStore implements ClaimStore {
        final List<Claim> preloadedClaims = new ArrayList<>();
        final List<TeamHome> preloadedHomes = new ArrayList<>();
        final List<UUID> savedClaims = new ArrayList<>();
        final List<UUID> deletedTeams = new ArrayList<>();
        boolean failOnSave;

        @Override
        public void initSchema() {
        }

        @Override
        public Collection<Claim> loadClaims() {
            return List.copyOf(preloadedClaims);
        }

        @Override
        public Collection<TeamHome> loadHomes() {
            return List.copyOf(preloadedHomes);
        }

        @Override
        public void saveClaims(UUID teamId, Collection<Claim> claims) {
            if (failOnSave) {
                throw new IllegalStateException("simulated write failure");
            }
            savedClaims.add(teamId);
        }

        @Override
        public void saveHome(TeamHome home) {
        }

        @Override
        public void deleteHome(UUID teamId, HomeType type) {
        }

        @Override
        public void deleteTeam(UUID teamId) {
            deletedTeams.add(teamId);
        }
    }
}
