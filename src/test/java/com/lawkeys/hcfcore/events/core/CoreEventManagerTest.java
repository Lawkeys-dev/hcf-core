package com.lawkeys.hcfcore.events.core;

import com.lawkeys.hcfcore.util.Cuboid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** DTC and Last Break, played without a server: one engine, {@link CoreEventManager}. */
class CoreEventManagerTest {

    private static final long SECOND = 1_000L;

    private final AtomicLong now = new AtomicLong(1_800_000_000_000L);
    private final UUID wizards = UUID.randomUUID();
    private final UUID warlocks = UUID.randomUUID();
    private CoreSettings settings;
    private CoreEventManager core;

    private static Cuboid zone() {
        return Cuboid.between("world", 0, 0, 0, 10, 10, 10);
    }

    private static CoreEventDefinition dtc(CounterMode counter, int breaks, long cooldown, List<Integer> announceAt,
                                           long maxSeconds) {
        return new CoreEventDefinition("dtc", CoreEventKind.DTC, "DTC", zone(), 5, 5, 5, "OBSIDIAN",
                counter, breaks, cooldown, announceAt, List.of(), maxSeconds, List.of());
    }

    private static CoreEventDefinition lastBreak(int breaks, long cooldown, long maxSeconds) {
        return new CoreEventDefinition("last-break", CoreEventKind.LAST_BREAK, "Last Break", zone(), 5, 5, 5,
                "OBSIDIAN", CounterMode.SHARED, breaks, cooldown, List.of(), List.of(), maxSeconds, List.of());
    }

    private void start(CoreEventDefinition definition) {
        settings = new CoreSettings(true, ZoneId.of("UTC"), List.of(definition));
        core = new CoreEventManager(() -> settings, now::get);
        core.start(definition);
    }

    private List<CoreUpdate> breakAfter(long seconds, UUID team) {
        now.addAndGet(seconds * SECOND);
        return core.recordBreak(team);
    }

    @BeforeEach
    void setUp() {
        start(dtc(CounterMode.SHARED, 5, 1L, List.of(), 0));
    }

    @Test
    void aBreakCostsOneCommonHealthPoint() {
        core.recordBreak(wizards);
        assertEquals(4, core.getCurrent().orElseThrow().health());
    }

    @Test
    void theCooldownIsPerTeam() {
        core.recordBreak(wizards);
        assertEquals(4, core.getCurrent().orElseThrow().health(), "the second, immediate break by the same team does not count");
        core.recordBreak(wizards);
        assertEquals(4, core.getCurrent().orElseThrow().health());
    }

    @Test
    void anotherTeamIsNotBlockedByTheFirstsCooldown() {
        core.recordBreak(wizards);
        core.recordBreak(warlocks);
        assertEquals(3, core.getCurrent().orElseThrow().health());
    }

    @Test
    void theCooldownExpiresWithTheClock() {
        core.recordBreak(wizards);
        breakAfter(1, wizards);
        assertEquals(3, core.getCurrent().orElseThrow().health(), "a second's wait let the same team break again");
    }

    @Test
    void aZeroCooldownNeverBlocks() {
        start(dtc(CounterMode.SHARED, 5, 0L, List.of(), 0));
        core.recordBreak(wizards);
        core.recordBreak(wizards);
        assertEquals(3, core.getCurrent().orElseThrow().health());
    }

    @Test
    void aTeamlessBreakIsRefused() {
        core.recordBreak(null);
        assertEquals(5, core.getCurrent().orElseThrow().health());
    }

    @Test
    void sharedTheMostBreaksWinsNotNecessarilyTheLastHit() {
        start(dtc(CounterMode.SHARED, 3, 0L, List.of(), 0));
        core.recordBreak(wizards); // health 3 -> 2, wizards: 1
        core.recordBreak(wizards); // health 2 -> 1, wizards: 2
        // Warlocks land the last hit that empties the health, with fewer breaks.
        List<CoreUpdate> last = core.recordBreak(warlocks); // health 1 -> 0
        assertEquals(CoreUpdate.Type.WON, last.get(0).type());
        assertEquals(wizards, last.get(0).teamId(), "wizards had the most breaks of their own");
    }

    @Test
    void sharedATieIsBrokenByWhoeverReachedItFirst() {
        start(dtc(CounterMode.SHARED, 4, 0L, List.of(), 0));
        core.recordBreak(wizards); // health 3, wizards: 1
        core.recordBreak(warlocks); // health 2, warlocks: 1
        core.recordBreak(wizards); // health 1, wizards: 2 (reached first)
        List<CoreUpdate> last = core.recordBreak(warlocks); // health 0, warlocks: 2 (reached second)
        assertEquals(CoreUpdate.Type.WON, last.get(0).type());
        assertEquals(wizards, last.get(0).teamId(), "both have 2 breaks, but wizards reached that count first");
    }

    @Test
    void perTeamTheFirstToTheTargetWinsAtOnce() {
        start(dtc(CounterMode.PER_TEAM, 2, 0L, List.of(), 0));
        core.recordBreak(wizards);
        List<CoreUpdate> last = core.recordBreak(wizards);
        assertEquals(CoreUpdate.Type.WON, last.get(last.size() - 1).type());
        assertEquals(wizards, last.get(last.size() - 1).teamId());
        assertTrue(core.getCurrent().isEmpty());
    }

    @Test
    void lastBreakGoesToWhoeverLandsTheFinalHitEvenWithFewerBreaksOfItsOwn() {
        start(lastBreak(3, 0L, 0));
        core.recordBreak(wizards);
        core.recordBreak(wizards);
        List<CoreUpdate> last = core.recordBreak(warlocks);
        assertEquals(CoreUpdate.Type.WON, last.get(0).type());
        assertEquals(warlocks, last.get(0).teamId(), "warlocks landed the last hit, despite fewer breaks");
    }

    @Test
    void milestonesAreAnnouncedOnceEachSharedByRemainingHealth() {
        start(dtc(CounterMode.SHARED, 5, 0L, List.of(3, 1), 0));
        assertTrue(core.recordBreak(wizards).isEmpty(), "health 4: no mark");
        List<CoreUpdate> at3 = core.recordBreak(wizards);
        assertEquals(1, at3.size());
        assertEquals(CoreUpdate.Type.MILESTONE, at3.get(0).type());
        assertTrue(core.recordBreak(warlocks).isEmpty(), "health 2: already past the next mark, nothing new yet");
    }

    @Test
    void milestonesArePerTeamUnderPerTeam() {
        start(dtc(CounterMode.PER_TEAM, 5, 0L, List.of(3), 0));
        // wizards: breaks 1 -> remaining 4 (no mark); 2 -> remaining 3 (mark).
        assertTrue(core.recordBreak(wizards).isEmpty());
        List<CoreUpdate> wizardMark = core.recordBreak(wizards);
        assertEquals(1, wizardMark.size());
        // warlocks starts fresh: its own remaining is still 5 -> 4, no mark yet.
        assertTrue(core.recordBreak(warlocks).isEmpty(), "each team's marks are its own");
    }

    @Test
    void aBreakAfterTheRunEndedCountsForNothing() {
        start(dtc(CounterMode.SHARED, 1, 0L, List.of(), 0));
        core.recordBreak(wizards);
        assertTrue(core.getCurrent().isEmpty());
        assertTrue(core.recordBreak(warlocks).isEmpty());
    }

    @Test
    void checkDoesNotConsumeTheCooldown() {
        start(dtc(CounterMode.SHARED, 5, 5L, List.of(), 0));
        assertTrue(core.check(wizards).isAllowed());
        assertTrue(core.check(wizards).isAllowed(), "asking twice changes nothing");
        core.recordBreak(wizards);
        assertFalse(core.check(wizards).isAllowed());
    }

    @Test
    void onlyOneCoreEventAtATime() {
        assertTrue(core.start(settings.definitions().get(0)).isEmpty());
        assertTrue(core.stop().isPresent());
        assertTrue(core.stop().isEmpty());
    }

    @Test
    void theTimeLimitEndsItWithNoWinner() {
        start(dtc(CounterMode.SHARED, 5, 0L, List.of(), 60));
        now.addAndGet(60 * SECOND);
        List<CoreUpdate> updates = core.tick();
        assertEquals(List.of(CoreUpdate.Type.EXPIRED), updates.stream().map(CoreUpdate::type).toList());
        assertTrue(core.getCurrent().isEmpty());
    }

    @Test
    void scheduleOpensTheEventAndTheFirstTickFiresNothing() {
        settings = new CoreSettings(true, ZoneId.of("UTC"), List.of());
        core = new CoreEventManager(() -> settings, now::get);
        assertTrue(core.tick().isEmpty(), "the first tick only records the window");
    }

    @Test
    void whileDisabledNothingMoves() {
        core.recordBreak(wizards);
        settings = new CoreSettings(false, ZoneId.of("UTC"), settings.definitions());
        now.addAndGet(600 * SECOND);
        core.tick();
        settings = new CoreSettings(true, ZoneId.of("UTC"), settings.definitions());
        assertEquals(4, core.getCurrent().orElseThrow().health());
    }

    @Test
    void isCoreOnlyMatchesTheRunningCore() {
        assertTrue(core.isCore("world", 5, 5, 5));
        assertFalse(core.isCore("world", 6, 5, 5));
        assertFalse(core.isCore("nether", 5, 5, 5));
        core.stop();
        assertFalse(core.isCore("world", 5, 5, 5), "no run, nothing matches");
    }

    @Test
    void aDefinitionRefusesACoreOutsideItsZone() {
        assertThrows(IllegalArgumentException.class, () -> new CoreEventDefinition("bad", CoreEventKind.DTC, "Bad",
                zone(), 50, 50, 50, "OBSIDIAN", CounterMode.SHARED, 5, 1L, List.of(), List.of(), 0, List.of()));
    }

    @Test
    void aDefinitionRefusesNonPositiveBreaks() {
        assertThrows(IllegalArgumentException.class, () -> new CoreEventDefinition("bad", CoreEventKind.DTC, "Bad",
                zone(), 5, 5, 5, "OBSIDIAN", CounterMode.SHARED, 0, 1L, List.of(), List.of(), 0, List.of()));
    }
}
