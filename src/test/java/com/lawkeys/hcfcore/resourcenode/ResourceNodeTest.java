package com.lawkeys.hcfcore.resourcenode;

import com.lawkeys.hcfcore.claim.ClaimManager;
import com.lawkeys.hcfcore.claim.ClaimMessages;
import com.lawkeys.hcfcore.claim.ClaimSettings;
import com.lawkeys.hcfcore.claim.ClaimStore;
import com.lawkeys.hcfcore.claim.ProtectionResult;
import com.lawkeys.hcfcore.team.Team;
import com.lawkeys.hcfcore.team.TeamEventDispatcher;
import com.lawkeys.hcfcore.team.TeamManager;
import com.lawkeys.hcfcore.team.TeamResult;
import com.lawkeys.hcfcore.team.TeamSettings;
import com.lawkeys.hcfcore.team.TeamStore;
import com.lawkeys.hcfcore.util.ChunkPosition;
import com.lawkeys.hcfcore.util.Cuboid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rule-level tests for the resource nodes of ARCHITECTURE.md section 9, family B.
 *
 * <p>Every refill here is decided by moving an injected clock, never by waiting:
 * a mountain on a four-hour timer is tested in microseconds. That is the whole
 * return on keeping the rule layer free of the server API.
 */
class ResourceNodeTest {

    private static final ZoneId UTC = ZoneId.of("UTC");
    /** A Monday at 10:00 UTC. Chosen for readability; nothing depends on the date. */
    private static final long MONDAY_10H = Instant.parse("2026-09-07T10:00:00Z").toEpochMilli();

    private static final long HOUR = 3_600_000L;
    private static final long MINUTE = 60_000L;

    private static final Cuboid REGION = Cuboid.between("world_nether", 40, 40, 40, 55, 60, 55);

    private final AtomicLong clock = new AtomicLong(MONDAY_10H);

    private ResourceNodeSettings settings;
    private ResourceNodeManager nodes;

    @BeforeEach
    void setUp() {
        settings = withNodes(mountain(RefillSchedule.everySeconds(4 * 3600)));
        nodes = new ResourceNodeManager(() -> settings, clock::get);
    }

    // --- helpers ----------------------------------------------------------

    private static BlockPalette glowstone() {
        return BlockPalette.of(new BlockPalette.Entry("GLOWSTONE", 1),
                new BlockPalette.Entry("NETHERRACK", 4));
    }

    private static ResourceNodeDefinition mountain(RefillSchedule schedule) {
        return mountain(schedule, List.of(), true, true);
    }

    private static ResourceNodeDefinition mountain(RefillSchedule schedule, List<Long> warnings,
                                                   boolean announceRefill, boolean preventClaim) {
        return new ResourceNodeDefinition("mountain", "&eGlowstone Mountain", REGION,
                glowstone(), RefillTargets.airOnly(), schedule, warnings,
                announceRefill, false, true, BreakPolicy.PALETTE_ONLY, preventClaim, true);
    }

    private ResourceNodeSettings withNodes(ResourceNodeDefinition... definitions) {
        return new ResourceNodeSettings(true, 1L, UTC, 4_000, false, true, List.of(definitions));
    }

    private void advance(long millis) {
        clock.addAndGet(millis);
    }

    /** Moves to that instant and ticks once, as the server layer would. */
    private List<NodeUpdate> tickAt(String utcInstant) {
        clock.set(Instant.parse(utcInstant).toEpochMilli());
        return nodes.tick();
    }

    private static boolean has(List<NodeUpdate> updates, NodeUpdate.Type type) {
        return updates.stream().anyMatch(update -> update.type() == type);
    }

    private static long count(List<NodeUpdate> updates, NodeUpdate.Type type) {
        return updates.stream().filter(update -> update.type() == type).count();
    }

    // ------------------------------------------------------------------

    @Nested
    class Scheduling {

        @Test
        void anIntervalIsAnchoredToMidnightNotToTheServerStart() {
            // 10:00 with a four-hour interval means 12:00 - the same 12:00 it would
            // have been had the server been up all night. This is the property that
            // makes a restart unable to shift a mountain.
            RefillSchedule every4h = RefillSchedule.everySeconds(4 * 3600);
            assertEquals(ZonedDateTime.parse("2026-09-07T12:00Z"),
                    every4h.next(ZonedDateTime.parse("2026-09-07T10:00Z")).orElseThrow());
            assertEquals(ZonedDateTime.parse("2026-09-07T12:00Z"),
                    every4h.next(ZonedDateTime.parse("2026-09-07T11:59:59Z")).orElseThrow());
        }

        @Test
        void afterTheLastSlotOfTheDayTheCycleRestartsAtMidnight() {
            RefillSchedule every4h = RefillSchedule.everySeconds(4 * 3600);
            assertEquals(ZonedDateTime.parse("2026-09-08T00:00Z"),
                    every4h.next(ZonedDateTime.parse("2026-09-07T21:30Z")).orElseThrow());
        }

        @Test
        void anIntervalThatDoesNotDivideTheDayLeavesAShortLastWindow() {
            // 7 hours: 00:00, 07:00, 14:00, 21:00, then midnight three hours later
            // rather than 04:00 the next day. Documented rather than hidden - the
            // alternative is refill times that drift a little every day.
            RefillSchedule every7h = RefillSchedule.everySeconds(7 * 3600);
            assertEquals(ZonedDateTime.parse("2026-09-07T21:00Z"),
                    every7h.next(ZonedDateTime.parse("2026-09-07T15:00Z")).orElseThrow());
            assertEquals(ZonedDateTime.parse("2026-09-08T00:00Z"),
                    every7h.next(ZonedDateTime.parse("2026-09-07T22:00Z")).orElseThrow());
        }

        @Test
        void explicitTimesAndAnIntervalAreMerged() {
            RefillSchedule both = new RefillSchedule(4 * 3600, List.of(LocalTime.of(10, 30)));
            assertEquals(ZonedDateTime.parse("2026-09-07T10:30Z"),
                    both.next(ZonedDateTime.parse("2026-09-07T10:00Z")).orElseThrow(),
                    "the soonest of the two sources wins");
            assertEquals(ZonedDateTime.parse("2026-09-07T12:00Z"),
                    both.next(ZonedDateTime.parse("2026-09-07T10:30Z")).orElseThrow());
        }

        @Test
        void anExplicitTimeRepeatsTheNextDay() {
            RefillSchedule noon = RefillSchedule.at(LocalTime.NOON);
            assertEquals(ZonedDateTime.parse("2026-09-08T12:00Z"),
                    noon.next(ZonedDateTime.parse("2026-09-07T18:00Z")).orElseThrow());
        }

        @Test
        void anEmptyScheduleHasNoNextOccurrence() {
            assertTrue(RefillSchedule.none().next(ZonedDateTime.parse("2026-09-07T10:00Z")).isEmpty());
            assertTrue(RefillSchedule.none().isEmpty());
        }

        @Test
        void theWindowIsHalfOpenSoAnOccurrenceFiresExactlyOnce() {
            RefillSchedule noon = RefillSchedule.at(LocalTime.NOON);
            long noonMillis = Instant.parse("2026-09-07T12:00:00Z").toEpochMilli();
            assertTrue(noon.occursWithin(UTC, noonMillis - 1000, noonMillis),
                    "the instant itself closes the window it belongs to");
            assertFalse(noon.occursWithin(UTC, noonMillis, noonMillis + 1000),
                    "and never opens the next one, or it would fire twice");
        }
    }

    @Nested
    class Refilling {

        @Test
        void theFirstTickFiresNothing() {
            // A server booting at 12:01 must not immediately refill every schedule
            // slot the night went through while it was down.
            assertTrue(tickAt("2026-09-07T12:01:00Z").isEmpty());
        }

        @Test
        void aRefillIsDueWhenTheWindowCrossesItsTime() {
            tickAt("2026-09-07T11:59:59Z");
            List<NodeUpdate> updates = tickAt("2026-09-07T12:00:01Z");
            assertTrue(has(updates, NodeUpdate.Type.REFILL_DUE));
            assertEquals("mountain", updates.get(0).nodeId());
        }

        @Test
        void aRefillFiresOnceNotOnEveryTickAfterwards() {
            tickAt("2026-09-07T11:59:59Z");
            tickAt("2026-09-07T12:00:01Z");
            assertTrue(tickAt("2026-09-07T12:00:02Z").isEmpty());
            assertTrue(tickAt("2026-09-07T12:30:00Z").isEmpty());
        }

        @Test
        void aDisabledModuleIsFrozenNotMerelyDeferred() {
            // The lesson the capture module learned the hard way: a disabled period
            // that keeps accumulating lands as one burst on the first tick after
            // re-enabling. Two days off would refill twelve times in one second.
            settings = new ResourceNodeSettings(false, 1L, UTC, 4_000, false, true, settings.nodes());
            tickAt("2026-09-07T10:00:00Z");
            assertTrue(tickAt("2026-09-09T10:00:00Z").isEmpty(), "nothing fires while disabled");

            settings = new ResourceNodeSettings(true, 1L, UTC, 4_000, false, true, settings.nodes());
            assertTrue(tickAt("2026-09-09T10:00:01Z").isEmpty(),
                    "and re-enabling does not replay the two days that passed");
        }

        @Test
        void aNodeWithNoScheduleNeverRefillsOnItsOwn() {
            settings = withNodes(mountain(RefillSchedule.none()));
            tickAt("2026-09-07T10:00:00Z");
            assertTrue(tickAt("2026-09-08T10:00:00Z").isEmpty());
        }

        @Test
        void staffCanRefillANodeThatHasNoSchedule() {
            settings = withNodes(mountain(RefillSchedule.none()));
            Optional<NodeUpdate> update = nodes.forceRefill("MOUNTAIN");
            assertTrue(update.isPresent(), "node ids are matched case-insensitively");
            assertEquals(NodeUpdate.Type.REFILL_DUE, update.get().type());
            assertTrue(nodes.forceRefill("nope").isEmpty());
        }

        @Test
        void aSilentNodeStillRefills() {
            settings = withNodes(mountain(RefillSchedule.everySeconds(4 * 3600), List.of(),
                    false, true));
            tickAt("2026-09-07T11:59:59Z");
            List<NodeUpdate> updates = tickAt("2026-09-07T12:00:01Z");

            assertTrue(has(updates, NodeUpdate.Type.REFILL_DUE),
                    "announce-refill: false silences the broadcast, it does not cancel the refill");
            assertFalse(updates.get(0).hasMessage());
        }

        @Test
        void theNextRefillIsReportedFromTheCurrentTime() {
            ResourceNodeDefinition node = settings.nodes().get(0);
            // Compared as an instant, not as a ZonedDateTime: the manager answers in
            // the configured zone ("UTC"), and a ZonedDateTime carrying ZoneId "UTC"
            // is not equal to one carrying the offset "Z" even at the same moment.
            assertEquals(Instant.parse("2026-09-07T12:00:00Z"),
                    nodes.getNextRefill(node, MONDAY_10H).orElseThrow().toInstant());
        }
    }

    @Nested
    class Warnings {

        @Test
        void aWarningLandsItsOwnNumberOfSecondsBeforeTheRefill() {
            settings = withNodes(mountain(RefillSchedule.everySeconds(4 * 3600),
                    List.of(300L), true, true));

            tickAt("2026-09-07T11:54:59Z");
            List<NodeUpdate> updates = tickAt("2026-09-07T11:55:01Z");
            assertEquals(1, count(updates, NodeUpdate.Type.REFILL_SOON));
            assertEquals("5m", updates.get(0).placeholders().get("time"));

            assertTrue(tickAt("2026-09-07T11:55:02Z").isEmpty(), "and only once");
        }

        @Test
        void severalWarningsCanBeConfigured() {
            settings = withNodes(mountain(RefillSchedule.everySeconds(4 * 3600),
                    List.of(300L, 60L), true, true));

            tickAt("2026-09-07T11:54:59Z");
            assertEquals(1, count(tickAt("2026-09-07T11:55:01Z"), NodeUpdate.Type.REFILL_SOON));
            assertEquals(1, count(tickAt("2026-09-07T11:59:01Z"), NodeUpdate.Type.REFILL_SOON));
        }

        @Test
        void aWarningAndItsRefillCanShareOneTickWithoutColliding() {
            // A tick slow enough to span both is not a special case: the warning is
            // about the next refill, the refill about this one, and both are true.
            settings = withNodes(mountain(RefillSchedule.at(LocalTime.NOON, LocalTime.of(12, 5)),
                    List.of(300L), true, true));

            tickAt("2026-09-07T11:59:59Z");
            List<NodeUpdate> updates = tickAt("2026-09-07T12:00:01Z");
            assertEquals(1, count(updates, NodeUpdate.Type.REFILL_DUE));
            assertEquals(1, count(updates, NodeUpdate.Type.REFILL_SOON),
                    "12:05 is five minutes away, so its warning is due in this same tick");
        }
    }

    @Nested
    class Palettes {

        @Test
        void weightsAreRelativeSharesNotPercentages() {
            BlockPalette palette = glowstone(); // 1 glowstone to 4 netherrack
            assertEquals(5L, palette.totalWeight());
            assertEquals("GLOWSTONE", palette.pick(0.0d));
            assertEquals("GLOWSTONE", palette.pick(0.19d));
            assertEquals("NETHERRACK", palette.pick(0.2d));
            assertEquals("NETHERRACK", palette.pick(0.99d));
        }

        @Test
        void aRollAtOrPastTheEndStillPicksSomething() {
            // Rather than throwing in the middle of a refill over a rounding artefact.
            assertEquals("NETHERRACK", glowstone().pick(1.0d));
            assertEquals("GLOWSTONE", glowstone().pick(-0.5d));
        }

        @Test
        void anEmptyPalettePicksNothing() {
            assertNull(BlockPalette.empty().pick(0.5d));
        }

        @Test
        void namespacedAndCasualNamesAreTheSameBlock() {
            // The server reports GLOWSTONE for a broken block; an operator may well
            // have written minecraft:glowstone. They must compare equal, or the
            // break policy would refuse mining the very block it just placed.
            BlockPalette palette = BlockPalette.of(new BlockPalette.Entry("minecraft:glowstone", 1));
            assertTrue(palette.contains("GLOWSTONE"));
            assertTrue(palette.contains("glowstone"));
            assertEquals(Set.of("GLOWSTONE"), palette.materials());
        }

        @Test
        void aWeightlessOrNamelessEntryIsRefusedOutright() {
            assertThrows(IllegalArgumentException.class,
                    () -> new BlockPalette.Entry("GLOWSTONE", 0));
            assertThrows(IllegalArgumentException.class,
                    () -> new BlockPalette.Entry("  ", 1));
        }

        @Test
        void aNodeWithNothingToPlaceIsNotANode() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ResourceNodeDefinition("empty", "Empty", REGION, BlockPalette.empty(),
                            RefillTargets.airOnly(), RefillSchedule.none(), List.of(),
                            true, false, true, BreakPolicy.PALETTE_ONLY, true, true));
        }
    }

    @Nested
    class WhatARefillOverwrites {

        @Test
        void byDefaultOnlyWhatPlayersMinedIsPutBack() {
            RefillTargets targets = RefillTargets.airOnly();
            assertTrue(targets.replaces("CAVE_AIR", true), "air has several names; the flag decides");
            assertFalse(targets.replaces("COBBLESTONE", false),
                    "a refill repairs the region, it does not rebuild over what is standing");
        }

        @Test
        void namedMaterialsCanAlsoBeOverwritten() {
            RefillTargets targets = new RefillTargets(true, Set.of("COBBLESTONE"));
            assertTrue(targets.replaces("cobblestone", false));
            assertFalse(targets.replaces("OBSIDIAN", false));
        }

        @Test
        void airCanBeLeftAloneEntirely() {
            RefillTargets targets = new RefillTargets(false, Set.of("COBBLESTONE"));
            assertFalse(targets.replaces("AIR", true));
        }
    }

    @Nested
    class Protection {

        @Test
        void onlyTheResourceItselfMayBeMinedByDefault() {
            ResourceNodeDefinition node = settings.nodes().get(0);
            assertTrue(node.allowsBreaking("GLOWSTONE"));
            assertTrue(node.allowsBreaking("NETHERRACK"));
            assertFalse(node.allowsBreaking("OBSIDIAN"),
                    "the structure survives, so there is still a mountain to refill");
        }

        @Test
        void anOpenPolicyLetsPlayersMineAnything() {
            ResourceNodeDefinition open = new ResourceNodeDefinition("mountain", "Mountain", REGION,
                    glowstone(), RefillTargets.airOnly(), RefillSchedule.none(), List.of(),
                    true, false, true, BreakPolicy.ANY, true, true);
            assertTrue(open.allowsBreaking("OBSIDIAN"));
        }

        @Test
        void explosionsCannotTakeAChunkOutOfTheMountain() {
            // Without this the break policy is decorative: a player refused the
            // netherrack blows it up, and the refill has less to fill every week.
            assertTrue(nodes.isExplosionProtected("world_nether", 41, 41, 41));
            assertFalse(nodes.isExplosionProtected("world_nether", 100, 41, 41),
                    "the ground around the mountain still blows up");
            assertFalse(nodes.isExplosionProtected("world", 41, 41, 41));
        }

        @Test
        void explosionProtectionCanBeTurnedOffPerNode() {
            settings = new ResourceNodeSettings(true, 1L, UTC, 4_000, false, true,
                    List.of(new ResourceNodeDefinition("mountain", "Mountain", REGION, glowstone(),
                            RefillTargets.airOnly(), RefillSchedule.none(), List.of(), true, false,
                            true, BreakPolicy.PALETTE_ONLY, true, false)));
            assertFalse(nodes.isExplosionProtected("world_nether", 41, 41, 41));
        }

        @Test
        void nothingMayBeCarriedOrPouredIntoTheRegion() {
            // What a piston or a flowing liquid is asked: the same "no building"
            // as a placed block, for the changes no player makes directly.
            assertTrue(nodes.isBuildProtected("world_nether", 41, 41, 41));
            assertFalse(nodes.isBuildProtected("world_nether", 100, 41, 41),
                    "outside the region, pistons and liquids behave as usual");
            assertFalse(nodes.isBuildProtected("world", 41, 41, 41));
        }

        @Test
        void aRegionThatAllowsBuildingAllowsPistonsAndLiquidsToo() {
            settings = new ResourceNodeSettings(true, 1L, UTC, 4_000, false, true,
                    List.of(new ResourceNodeDefinition("mountain", "Mountain", REGION, glowstone(),
                            RefillTargets.airOnly(), RefillSchedule.none(), List.of(), true, false,
                            false, BreakPolicy.PALETTE_ONLY, true, true)));
            assertFalse(nodes.isBuildProtected("world_nether", 41, 41, 41));
        }

        @Test
        void aDisabledModuleStopsNoPistonAndNoLiquid() {
            settings = new ResourceNodeSettings(false, 1L, UTC, 4_000, false, true, settings.nodes());
            assertFalse(nodes.isBuildProtected("world_nether", 41, 41, 41));
        }

        @Test
        void disablingTheModuleReallyDisablesIt() {
            // Not a half-measure: with enabled: false the command says the module is
            // off, so the regions must stop refusing builds, explosions and claims
            // too. Anything else is a server telling its operator one thing and its
            // players another.
            settings = new ResourceNodeSettings(false, 1L, UTC, 4_000, false, true, settings.nodes());

            assertTrue(nodes.nodeAtBlock("world_nether", 41, 41, 41).isEmpty());
            assertTrue(nodes.nodeAt("world_nether", 41.5d, 41.0d, 41.5d).isEmpty());
            assertFalse(nodes.isExplosionProtected("world_nether", 41, 41, 41));
            assertTrue(nodes.getStartupFills().isEmpty());
            assertEquals(1, nodes.getNodes().size(),
                    "the node is still configured, it is simply not in force");
        }

        @Test
        void aNodeIsFoundByTheBlockAndTheChunkItCovers() {
            assertTrue(nodes.nodeAtBlock("world_nether", 41, 41, 41).isPresent());
            assertTrue(nodes.nodeAtBlock("world_nether", 55, 60, 55).isPresent(), "bounds included");
            assertTrue(nodes.nodeAtBlock("world_nether", 56, 60, 55).isEmpty());
            assertTrue(nodes.nodeAtBlock("world", 41, 41, 41).isEmpty(), "wrong world");
            assertTrue(nodes.nodeAt("world_nether", 41.5d, 41.0d, 41.5d).isPresent());
        }
    }

    /**
     * The seam with {@code claim/}, wired to the real managers rather than to a
     * stub - the same way the {@code ReclaimLoop} group checks the DTR seam.
     */
    @Nested
    class TheClaimSeam {

        private static final Cuboid IN_THE_OVERWORLD =
                Cuboid.between("world", 0, 40, 0, 15, 60, 15);

        private TeamManager teams;
        private ClaimManager claims;
        private Team wizards;

        @BeforeEach
        void wireTheModules() {
            AtomicLong now = new AtomicLong(MONDAY_10H);
            teams = new TeamManager(TeamSettings::defaults, TeamStore.NO_OP,
                    TeamEventDispatcher.NO_OP, now::get);
            ClaimSettings claimSettings = ClaimSettings.defaults();
            claims = new ClaimManager(() -> claimSettings, teams, ClaimStore.NO_OP, now::get);
            wizards = teams.createTeam(UUID.randomUUID(), "Wizards").getTeam().orElseThrow();
        }

        private void useNode(boolean preventClaim) {
            settings = new ResourceNodeSettings(true, 1L, UTC, 4_000, false, true,
                    List.of(new ResourceNodeDefinition("mountain", "&eGlowstone Mountain",
                            IN_THE_OVERWORLD, glowstone(), RefillTargets.airOnly(),
                            RefillSchedule.none(), List.of(), true, false, true,
                            BreakPolicy.PALETTE_ONLY, preventClaim, true)));
            claims.setReservedRegionPolicy(nodes);
        }

        /** @return a claim manager with a 200-block warzone around the overworld's centre */
        private ClaimManager claimsWithWarzone() {
            ClaimSettings base = ClaimSettings.defaults();
            ClaimSettings withWarzone = new ClaimSettings(base.enabled(), base.sizes(), base.price(),
                    base.placement(), base.protection(), base.homes(), base.claimableWorlds(), base.requiredRoles(),
                    new ClaimSettings.WarzoneRules("&cWarzone", false,
                            java.util.Map.of("world", new ClaimSettings.WarzoneRules.Area(0, 0, 200))),
                    base.stuck(), base.wand(), base.mapCellBlocks());
            return new ClaimManager(() -> withWarzone, teams, ClaimStore.NO_OP, () -> MONDAY_10H);
        }

        /**
         * The Mountain in the warzone FEATURES.md section 6 describes: its region
         * follows its own rules, the warzone's "no building" everywhere else.
         */
        @Test
        void aMountainInsideTheWarzoneStaysMinableWhileTheRestOfItDoesNot() {
            ClaimManager warzone = claimsWithWarzone();
            useNode(true);
            warzone.setReservedRegionPolicy(nodes);

            assertEquals(ProtectionResult.ALLOWED, warzone.checkBuild(wizards, "world", 5, 50, 5),
                    "inside the region, the Mountain's own listener decides");
            assertEquals(ProtectionResult.DENIED_WARZONE, warzone.checkBuild(wizards, "world", 5, 61, 5),
                    "one block above it, same chunk, is warzone again");
            assertEquals(ProtectionResult.DENIED_WARZONE, warzone.checkBuild(wizards, "world", 50, 50, 50));

            settings = new ResourceNodeSettings(false, 1L, UTC, 4_000, false, true, settings.nodes());
            assertEquals(ProtectionResult.DENIED_WARZONE, warzone.checkBuild(wizards, "world", 5, 50, 5),
                    "a disabled module governs nothing, so the warzone takes its land back");
        }

        /**
         * Explosions follow the same handover as mining: inside the region, the
         * Mountain's own setting decides. Checked with a node that lets explosions
         * through, since one that refuses them would pass whoever decided.
         */
        @Test
        void aMountainInsideTheWarzoneDecidesItsOwnExplosions() {
            ClaimManager warzone = claimsWithWarzone();
            settings = new ResourceNodeSettings(true, 1L, UTC, 4_000, false, true,
                    List.of(new ResourceNodeDefinition("mountain", "&eGlowstone Mountain",
                            IN_THE_OVERWORLD, glowstone(), RefillTargets.airOnly(),
                            RefillSchedule.none(), List.of(), true, false, true,
                            BreakPolicy.PALETTE_ONLY, true, false)));
            warzone.setReservedRegionPolicy(nodes);

            assertFalse(nodes.isExplosionProtected("world", 5, 50, 5));
            assertFalse(warzone.isExplosionProtected("world", 5, 50, 5),
                    "the node lets explosions through, and the warzone must not overrule it");
            assertTrue(warzone.isExplosionProtected("world", 5, 61, 5),
                    "one block above the region, same chunk, the warzone decides again");
        }

        @Test
        void aTeamCannotClaimTheGroundAMountainStandsOn() {
            useNode(true);
            TeamResult refused = claims.claim(wizards, null, "world", 0, 0, 15, 15);

            assertFalse(refused.isSuccess());
            assertEquals(ClaimMessages.CLAIM_RESERVED_REGION, refused.getMessageKey());
            assertEquals("&eGlowstone Mountain", refused.getPlaceholders().get("region"),
                    "the refusal names what is in the way");
            assertEquals(0, claims.getClaimCount(wizards.getId()));
        }

        @Test
        void thelandNextToItIsStillFree() {
            useNode(true);
            assertTrue(claims.claim(wizards, null, "world", 480, 480, 495, 495)
                    .isSuccess());
        }

        @Test
        void aNodeThatDoesNotReserveItsRegionLeavesTheLandClaimable() {
            useNode(false);
            assertTrue(claims.claim(wizards, null, "world", 0, 0, 15, 15)
                    .isSuccess());
        }

        @Test
        void withoutTheModuleNothingIsReserved() {
            // The default the claim module ships with: written first, it must work
            // on a server where no module owns any region at all.
            useNode(true);
            claims.setReservedRegionPolicy(
                    com.lawkeys.hcfcore.claim.ReservedRegionPolicy.NONE);
            assertTrue(claims.claim(wizards, null, "world", 0, 0, 15, 15)
                    .isSuccess());
        }

        @Test
        void aDisabledModuleReservesNothing() {
            useNode(true);
            settings = new ResourceNodeSettings(false, 1L, UTC, 4_000, false, true, settings.nodes());

            assertTrue(nodes.reservedRegionAt(new ChunkPosition("world", 0, 0)).isEmpty());
            assertTrue(claims.claim(wizards, null, "world", 0, 0, 15, 15)
                    .isSuccess());
        }

        @Test
        void theSeamAnswersWithTheRegionName() {
            useNode(true);
            assertEquals(Optional.of("&eGlowstone Mountain"),
                    nodes.reservedRegionAt(new ChunkPosition("world", 0, 0)));
            assertSame(Optional.empty(), nodes.reservedRegionAt(new ChunkPosition("world", 9, 9)));
        }
    }

    @Nested
    class TheWalk {

        @Test
        void everyBlockIsVisitedExactlyOnceWhateverTheBatchSize() {
            Cuboid region = Cuboid.between("world", -2, 5, -3, 1, 6, 0); // 4 x 2 x 4 = 32
            for (int batch : new int[] {1, 5, 32, 1000}) {
                RefillCursor cursor = new RefillCursor(region);
                Set<String> seen = new HashSet<>();
                List<String> order = new ArrayList<>();
                while (!cursor.isDone()) {
                    cursor.advance(batch, (x, y, z) -> {
                        String key = x + ":" + y + ":" + z;
                        seen.add(key);
                        order.add(key);
                    });
                }
                assertEquals(32, order.size(), "batch size " + batch + " changed the work done");
                assertEquals(32, seen.size(), "batch size " + batch + " visited a block twice");
                assertEquals(region.blockCount(), cursor.visited());
                assertEquals(0L, cursor.remaining());
            }
        }

        @Test
        void aSingleBlockRegionTakesOneStep() {
            RefillCursor cursor = new RefillCursor(Cuboid.between("world", 3, 3, 3, 3, 3, 3));
            assertEquals(1, cursor.advance(64, (x, y, z) -> {
                assertEquals(3, x);
                assertEquals(3, y);
                assertEquals(3, z);
            }));
            assertTrue(cursor.isDone());
        }

        @Test
        void aFinishedWalkDoesNotStartASecondLap() {
            RefillCursor cursor = new RefillCursor(Cuboid.between("world", 0, 0, 0, 1, 1, 1));
            cursor.advance(64, (x, y, z) -> { });
            assertTrue(cursor.isDone());
            assertEquals(0, cursor.advance(64, (x, y, z) -> {
                throw new AssertionError("the walk was over");
            }));
            assertEquals(8L, cursor.visited());
        }
    }

    /**
     * The bookkeeping behind the chunk tickets a refill holds. A plugin gets one
     * ticket per chunk, so two mountains sharing a chunk must not release it under
     * each other.
     */
    @Nested
    class ChunkHolding {

        /** Blocks 0..31 on x: chunks 0 and 1. */
        private final Cuboid west = Cuboid.between("world", 0, 60, 0, 31, 80, 15);
        /** Blocks 16..47 on x: chunks 1 and 2 - chunk 1 is shared with {@link #west}. */
        private final Cuboid east = Cuboid.between("world", 16, 60, 0, 47, 80, 15);

        private final Object westRefill = new Object();
        private final Object eastRefill = new Object();

        private ChunkPosition chunk(int x) {
            return new ChunkPosition("world", x, 0);
        }

        @Test
        void aLoneRefillHoldsAndReleasesEveryChunkOfItsRegion() {
            ChunkHolds holds = new ChunkHolds();
            assertEquals(Set.of(chunk(0), chunk(1)), Set.copyOf(holds.hold(westRefill, west)));
            assertEquals(Set.of(chunk(0), chunk(1)), Set.copyOf(holds.release(westRefill)));
            assertTrue(holds.isEmpty());
        }

        @Test
        void aSharedChunkStaysHeldUntilItsLastRefillEnds() {
            ChunkHolds holds = new ChunkHolds();
            holds.hold(westRefill, west);

            assertEquals(List.of(chunk(2)), holds.hold(eastRefill, east), "chunk 1 already has a ticket");
            assertEquals(List.of(chunk(0)), holds.release(westRefill),
                    "the first refill to finish must not free the chunk the second is still filling");
            assertEquals(Set.of(chunk(1), chunk(2)), Set.copyOf(holds.release(eastRefill)));
            assertTrue(holds.isEmpty());
        }

        @Test
        void aReleaseByAnyoneElseFreesNothing() {
            // What a plain reference count gets wrong: it cannot tell who holds a
            // chunk, so this release would have decremented chunk 1 and freed it
            // under the refill that is still filling it.
            ChunkHolds holds = new ChunkHolds();
            holds.hold(westRefill, west);

            assertTrue(holds.release(eastRefill).isEmpty());
            assertEquals(List.of(chunk(0), chunk(1)), holds.release(westRefill));
        }

        @Test
        void releasingTwiceOrHoldingTwiceChangesNothing() {
            ChunkHolds holds = new ChunkHolds();
            holds.hold(westRefill, west);
            assertTrue(holds.hold(westRefill, west).isEmpty());

            assertEquals(2, holds.release(westRefill).size());
            assertTrue(holds.release(westRefill).isEmpty(), "a second release has nothing left to give back");
        }
    }

    /**
     * Which blocks a body is in, so a refill landing mid-fight does not fill the air
     * around a player and suffocate them. A player's box is 0.6 wide and 1.8 tall.
     */
    @Nested
    class Bodies {

        private final Occupancy occupancy = new Occupancy(REGION);

        /** A standing player whose feet are at (x, y, z). */
        private void standAt(double x, double y, double z) {
            occupancy.addBody(x - 0.3, y, z - 0.3, x + 0.3, y + 1.8, z + 0.3);
        }

        @Test
        void aStandingPlayerOccupiesTheirFeetAndHeadBlocks() {
            standAt(45.5, 50.0, 45.5);

            assertTrue(occupancy.contains(45, 50, 45), "feet");
            assertTrue(occupancy.contains(45, 51, 45), "head - the block they would suffocate in");
            assertFalse(occupancy.contains(45, 52, 45));
            assertFalse(occupancy.contains(46, 50, 45));
        }

        @Test
        void aPlayerStraddlingTwoColumnsOccupiesBoth() {
            standAt(46.0, 50.0, 45.5); // x from 45.7 to 46.3

            assertTrue(occupancy.contains(45, 50, 45));
            assertTrue(occupancy.contains(46, 50, 45));
            assertTrue(occupancy.contains(46, 51, 45));
        }

        @Test
        void aFaceExactlyOnABlockBoundaryDoesNotReachIntoTheNextBlock() {
            occupancy.addBody(44.4, 50.0, 45.2, 45.0, 51.0, 45.8);

            assertTrue(occupancy.contains(44, 50, 45));
            assertFalse(occupancy.contains(45, 50, 45), "a box ending at 45.0 is not in block 45");
            assertFalse(occupancy.contains(44, 51, 45), "nor one ending at y 51.0 in block 51");
        }

        @Test
        void negativeCoordinatesLandInTheRightBlock() {
            Occupancy below = new Occupancy(Cuboid.between("world", -10, 0, -10, 0, 10, 0));
            below.addBody(-0.8, 5.0, -0.8, -0.2, 6.8, -0.2);

            assertTrue(below.contains(-1, 5, -1), "-0.8 to -0.2 is block -1, not block 0");
            assertFalse(below.contains(0, 5, 0));
        }

        @Test
        void aBodyNowhereNearTheRegionIsIgnored() {
            standAt(500.5, 50.0, 500.5);
            assertTrue(occupancy.isEmpty());
        }
    }
}
