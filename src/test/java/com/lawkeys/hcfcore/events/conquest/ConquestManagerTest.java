package com.lawkeys.hcfcore.events.conquest;

import com.lawkeys.hcfcore.events.ContestPolicy;
import com.lawkeys.hcfcore.events.Occupant;
import com.lawkeys.hcfcore.events.Standing;
import com.lawkeys.hcfcore.util.Cuboid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Conquest, played without a server: the classic HCF ruleset the project owner
 * chose on 12/09/2026.
 */
class ConquestManagerTest {

    private static final long SECOND = 1_000L;

    private final AtomicLong now = new AtomicLong(1_800_000_000_000L);
    private final UUID wizards = UUID.randomUUID();
    private final UUID warlocks = UUID.randomUUID();
    private ConquestSettings settings;
    private ConquestManager conquest;

    private static ConquestZone zone(String id) {
        return new ConquestZone(id, id, Cuboid.between("world", 0, 0, 0, 1, 1, 1));
    }

    /** Two zones, 30 seconds, 1 point a capture, 3 to win, 2 lost per death. */
    private ConquestDefinition definition(ContestPolicy policy, long maxSeconds) {
        return new ConquestDefinition("conquest", "Conquest", List.of(zone("red"), zone("blue")),
                30, 1, 3, 2, policy, List.of(), maxSeconds, List.of());
    }

    @BeforeEach
    void setUp() {
        settings = new ConquestSettings(true, ZoneId.of("UTC"), true, List.of(definition(ContestPolicy.RESET, 0)));
        conquest = new ConquestManager(() -> settings, now::get);
        conquest.start(settings.definitions().get(0));
    }

    private List<ConquestUpdate> tickAfter(long seconds, Map<String, List<Occupant>> occupants) {
        now.addAndGet(seconds * SECOND);
        return conquest.tick(occupants);
    }

    private static Map<String, List<Occupant>> in(String zone, UUID... teams) {
        Map<String, List<Occupant>> occupants = new HashMap<>();
        List<Occupant> list = new java.util.ArrayList<>();
        for (UUID team : teams) {
            list.add(Occupant.of(UUID.randomUUID(), team));
        }
        occupants.put(zone, list);
        return occupants;
    }

    private int points(UUID team) {
        return conquest.getCurrent().map(run -> run.points(team)).orElse(-1);
    }

    @Test
    void holdingAZoneScoresAndTheZoneStartsAgain() {
        conquest.tick(in("red", wizards));
        assertTrue(tickAfter(29, in("red", wizards)).isEmpty());
        List<ConquestUpdate> captured = tickAfter(1, in("red", wizards));
        assertEquals(ConquestUpdate.Type.ZONE_CAPTURED, captured.get(0).type());
        assertEquals(1, points(wizards));

        tickAfter(30, in("red", wizards));
        assertEquals(2, points(wizards), "the same team keeps scoring from the zone it holds");
    }

    @Test
    void theFirstTeamToTheTargetWinsAndTheConquestEnds() {
        conquest.tick(in("red", wizards));
        tickAfter(30, in("red", wizards));
        tickAfter(30, in("red", wizards));
        List<ConquestUpdate> last = tickAfter(30, in("red", wizards));
        assertEquals(ConquestUpdate.Type.WON, last.get(last.size() - 1).type());
        assertEquals(wizards, last.get(last.size() - 1).teamId());
        assertTrue(conquest.getCurrent().isEmpty());
    }

    /** The zones are captured in parallel: holding two scores twice as fast. */
    @Test
    void twoZonesScoreInParallel() {
        Map<String, List<Occupant>> both = new HashMap<>(in("red", wizards));
        both.putAll(in("blue", wizards));
        conquest.tick(both);
        tickAfter(30, both);
        assertEquals(2, points(wizards));
    }

    @Test
    void aContestedZoneIsFrozen() {
        conquest.tick(in("red", wizards));
        tickAfter(20, in("red", wizards));
        tickAfter(60, in("red", wizards, warlocks));
        assertEquals(0, points(wizards), "nothing scored while contested");
        tickAfter(10, in("red", wizards));
        assertEquals(1, points(wizards), "and it resumes where it was: 20 + 10 seconds");
    }

    @Test
    void underResetAnotherTeamTakingOverStartsFromFull() {
        conquest.tick(in("red", wizards));
        tickAfter(20, in("red", wizards));
        tickAfter(20, in("red", warlocks));
        assertEquals(0, points(warlocks));
        tickAfter(10, in("red", warlocks));
        assertEquals(1, points(warlocks), "30 seconds of its own, not the 10 left by the others");
    }

    @Test
    void underPauseTheCountdownCarriesOverToWhoeverHoldsNext() {
        settings = new ConquestSettings(true, ZoneId.of("UTC"), true, List.of(definition(ContestPolicy.PAUSE, 0)));
        conquest = new ConquestManager(() -> settings, now::get);
        conquest.start(settings.definitions().get(0));
        conquest.tick(in("red", wizards));
        tickAfter(20, in("red", wizards));
        tickAfter(1, Map.of());
        tickAfter(10, in("red", warlocks));
        assertEquals(1, points(warlocks));
    }

    @Test
    void aDeathCostsPointsButNeverBelowZero() {
        conquest.tick(in("red", wizards));
        tickAfter(30, in("red", wizards));
        assertEquals(1, points(wizards));

        ConquestUpdate lost = conquest.recordDeath(wizards).orElseThrow();
        assertEquals("1", lost.placeholders().get("lost"), "only what it had");
        assertEquals(0, points(wizards));
        assertTrue(conquest.recordDeath(wizards).isEmpty(), "nothing left to lose, nothing announced");
        assertTrue(conquest.recordDeath(null).isEmpty(), "a teamless death costs nobody");
    }

    @Test
    void theTimeLimitEndsItWithNoWinner() {
        settings = new ConquestSettings(true, ZoneId.of("UTC"), true, List.of(definition(ContestPolicy.RESET, 60)));
        conquest = new ConquestManager(() -> settings, now::get);
        conquest.start(settings.definitions().get(0));
        List<ConquestUpdate> updates = tickAfter(60, in("red", wizards));
        assertEquals(List.of(ConquestUpdate.Type.EXPIRED), updates.stream().map(ConquestUpdate::type).toList());
        assertTrue(conquest.getCurrent().isEmpty());
    }

    @Test
    void oneConquestAtATime() {
        assertTrue(conquest.start(settings.definitions().get(0)).isEmpty());
        assertTrue(conquest.stop().isPresent());
        assertTrue(conquest.stop().isEmpty());
    }

    /** Disabled means frozen: the time spent off must not hand out a capture on the way back. */
    @Test
    void whileDisabledNothingMoves() {
        conquest.tick(in("red", wizards));
        settings = new ConquestSettings(false, ZoneId.of("UTC"), true, settings.definitions());
        tickAfter(600, in("red", wizards));
        settings = new ConquestSettings(true, ZoneId.of("UTC"), true, settings.definitions());
        tickAfter(1, in("red", wizards));
        assertEquals(0, points(wizards));
    }

    @Test
    void theStandingsPutTheLeaderFirst() {
        conquest.tick(in("red", wizards));
        tickAfter(30, in("red", wizards));
        Map<String, List<Occupant>> split = new HashMap<>(in("red", wizards));
        split.putAll(in("blue", warlocks));
        tickAfter(30, split);
        List<Standing> standings = conquest.getCurrent().orElseThrow().standings();
        assertEquals(wizards, standings.get(0).teamId());
        assertEquals(2, standings.get(0).points());
        assertFalse(standings.size() < 2);
    }
}
