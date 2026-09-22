package com.lawkeys.hcfcore.events.slide;

import com.lawkeys.hcfcore.events.Occupant;
import com.lawkeys.hcfcore.events.Standing;
import com.lawkeys.hcfcore.util.Cuboid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Slide, played without a server. */
class SlideManagerTest {

    private static final long SECOND = 1_000L;

    private final AtomicLong now = new AtomicLong(1_800_000_000_000L);
    private final UUID wizards = UUID.randomUUID();
    private final UUID warlocks = UUID.randomUUID();
    private SlideSettings settings;
    private SlideManager slide;

    private static Cuboid zone() {
        return Cuboid.between("world", 0, 0, 0, 10, 10, 10);
    }

    private static SlideDefinition definition(int pointsPerPlayer, long intervalSeconds, int deathPenalty,
                                              int pointsToWin, List<Integer> announceAt, long maxSeconds) {
        return new SlideDefinition("slide", "Slide", zone(), pointsPerPlayer, intervalSeconds, deathPenalty,
                true, pointsToWin, announceAt, List.of(), maxSeconds, List.of());
    }

    private void start(SlideDefinition definition) {
        settings = new SlideSettings(true, ZoneId.of("UTC"), List.of(definition));
        slide = new SlideManager(() -> settings, now::get);
        slide.start(definition);
    }

    private static List<Occupant> occupants(UUID team, int count) {
        List<Occupant> occupants = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            occupants.add(Occupant.of(UUID.randomUUID(), team));
        }
        return occupants;
    }

    private static List<Occupant> merge(List<Occupant> a, List<Occupant> b) {
        List<Occupant> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }

    private List<SlideUpdate> tickAfter(long seconds, List<Occupant> inZone) {
        now.addAndGet(seconds * SECOND);
        return slide.tick(inZone);
    }

    private int points(UUID team) {
        return slide.getCurrent().map(run -> run.points(team)).orElse(-1);
    }

    @BeforeEach
    void setUp() {
        start(definition(1, 1, 10, 500, List.of(), 0));
    }

    @Test
    void everyMemberInTheZoneScoresEachIntervalCumulatively() {
        tickAfter(1, occupants(wizards, 3));
        assertEquals(3, points(wizards));
    }

    @Test
    void teamsScoreInParallel() {
        List<Occupant> both = merge(occupants(wizards, 2), occupants(warlocks, 1));
        tickAfter(1, both);
        assertEquals(2, points(wizards));
        assertEquals(1, points(warlocks));
    }

    @Test
    void aTeamlessPlayerScoresNothing() {
        tickAfter(1, List.of(Occupant.of(UUID.randomUUID(), null)));
        assertEquals(0, points(wizards));
        assertEquals(0, points(warlocks));
    }

    @Test
    void alliedTeamsAreScoredSeparately() {
        // "Allied" here means nothing to this engine: an ally is just another
        // team id, scored on its own, exactly like an enemy.
        List<Occupant> both = merge(occupants(wizards, 1), occupants(warlocks, 1));
        tickAfter(1, both);
        tickAfter(1, both);
        assertEquals(2, points(wizards));
        assertEquals(2, points(warlocks));
    }

    @Test
    void aLongerIntervalOnlyScoresAtItsEnd() {
        start(definition(1, 10, 10, 500, List.of(), 0));
        tickAfter(9, occupants(wizards, 1));
        assertEquals(0, points(wizards));
        tickAfter(1, occupants(wizards, 1));
        assertEquals(1, points(wizards));
    }

    @Test
    void aLateTickCatchesUpAndCarriesOverTheRest() {
        start(definition(1, 10, 10, 500, List.of(), 0));
        tickAfter(25, occupants(wizards, 1));
        assertEquals(2, points(wizards), "two whole intervals in 25 seconds");
        tickAfter(5, occupants(wizards, 1));
        assertEquals(3, points(wizards), "the 5 leftover seconds plus this tick's 5 make a third interval");
    }

    @Test
    void aDeathCostsPointsButNeverBelowZero() {
        tickAfter(1, occupants(wizards, 3));
        assertEquals(3, points(wizards));
        SlideUpdate lost = slide.recordDeath(wizards).orElseThrow();
        assertEquals("3", lost.placeholders().get("lost"), "only what it had");
        assertEquals(0, points(wizards));
        assertTrue(slide.recordDeath(wizards).isEmpty(), "nothing left to lose, nothing announced");
    }

    @Test
    void aDeathWithNoRunOrNoTeamCostsNothing() {
        assertTrue(slide.recordDeath(null).isEmpty());
        slide.stop();
        assertTrue(slide.recordDeath(wizards).isEmpty());
    }

    @Test
    void theFirstTeamToTheTargetWins() {
        start(definition(1, 1, 10, 3, List.of(), 0));
        List<SlideUpdate> last = tickAfter(3, occupants(wizards, 1));
        assertEquals(SlideUpdate.Type.WON, last.get(last.size() - 1).type());
        assertEquals(wizards, last.get(last.size() - 1).teamId());
        assertTrue(slide.getCurrent().isEmpty());
    }

    @Test
    void twoTeamsCrossingTogetherHandsItToTheHighest() {
        start(definition(1, 1, 10, 3, List.of(), 0));
        List<Occupant> both = merge(occupants(wizards, 4), occupants(warlocks, 3));
        List<SlideUpdate> last = tickAfter(1, both);
        assertEquals(SlideUpdate.Type.WON, last.get(last.size() - 1).type());
        assertEquals(wizards, last.get(last.size() - 1).teamId(), "4 points beats 3, both over the target");
    }

    @Test
    void anExactTieChangesNothing() {
        start(definition(1, 1, 10, 3, List.of(), 0));
        List<Occupant> both = merge(occupants(wizards, 3), occupants(warlocks, 3));
        List<SlideUpdate> updates = tickAfter(1, both);
        assertTrue(updates.stream().noneMatch(update -> update.type() == SlideUpdate.Type.WON),
                "an exact tie over the target wins nobody");
        assertTrue(slide.getCurrent().isPresent(), "the Slide keeps running");
    }

    @Test
    void theTopThreeAreOrderedAndStable() {
        UUID third = UUID.randomUUID();
        List<Occupant> all = merge(merge(occupants(wizards, 3), occupants(warlocks, 2)), occupants(third, 1));
        tickAfter(1, all);
        List<Standing> top = slide.getCurrent().orElseThrow().top(3);
        assertEquals(List.of(wizards, warlocks, third), top.stream().map(Standing::teamId).toList());
    }

    @Test
    void milestonesAreAnnouncedOncePerTeam() {
        start(definition(1, 1, 10, 500, List.of(3), 0));
        assertTrue(tickAfter(1, occupants(wizards, 2)).isEmpty(), "2 points: no mark yet");
        List<SlideUpdate> at3 = tickAfter(1, occupants(wizards, 1));
        assertEquals(1, at3.size());
        assertEquals(SlideUpdate.Type.MILESTONE, at3.get(0).type());
        assertTrue(tickAfter(1, occupants(wizards, 1)).isEmpty(), "already past the mark, nothing new");
    }

    @Test
    void theTimeLimitEndsItWithNoWinner() {
        start(definition(1, 1, 10, 500, List.of(), 30));
        List<SlideUpdate> updates = tickAfter(30, occupants(wizards, 1));
        assertEquals(List.of(SlideUpdate.Type.EXPIRED), updates.stream().map(SlideUpdate::type).toList());
        assertTrue(slide.getCurrent().isEmpty());
    }

    @Test
    void onlyOneSlideAtATime() {
        assertTrue(slide.start(settings.definitions().get(0)).isEmpty());
        assertTrue(slide.stop().isPresent());
        assertTrue(slide.stop().isEmpty());
    }

    @Test
    void scheduleOpensTheEventAndTheFirstTickFiresNothing() {
        settings = new SlideSettings(true, ZoneId.of("UTC"), List.of());
        slide = new SlideManager(() -> settings, now::get);
        assertTrue(slide.tick(List.of()).isEmpty(), "the first tick only records the window");
    }

    @Test
    void whileDisabledNothingMoves() {
        tickAfter(1, occupants(wizards, 1));
        settings = new SlideSettings(false, ZoneId.of("UTC"), settings.definitions());
        tickAfter(600, occupants(wizards, 1));
        settings = new SlideSettings(true, ZoneId.of("UTC"), settings.definitions());
        assertEquals(1, points(wizards));
    }
}
