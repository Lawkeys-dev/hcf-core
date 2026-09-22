package com.lawkeys.hcfcore.events.totem;

import com.lawkeys.hcfcore.util.Cuboid;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Totem's rules, without a server. */
class TotemManagerTest {

    private static final UUID WIZARDS = UUID.randomUUID();
    private static final UUID RAIDERS = UUID.randomUUID();
    private static final Cuboid ZONE = Cuboid.between("world", 0, 60, 0, 20, 80, 20);

    private final AtomicLong now = new AtomicLong(1_000_000L);
    private TotemSettings settings = new TotemSettings(true, ZoneOffset.UTC, List.of(totem(5, RivalBreak.RESET)));
    private final TotemManager manager = new TotemManager(() -> settings, now::get);

    private static TotemDefinition totem(int height, RivalBreak rival) {
        return new TotemDefinition("totem", "Totem", ZONE, 10, 64, 10, height, "QUARTZ_BLOCK", "BEDROCK", "BEDROCK",
                List.of("DIAMOND_SWORD"), true, rival, true, List.of(), 0L, List.of());
    }

    private TotemRun start(TotemDefinition definition) {
        settings = new TotemSettings(true, ZoneOffset.UTC, List.of(definition));
        assertTrue(manager.start(definition).isPresent());
        return manager.getCurrent().orElseThrow();
    }

    private static TotemUpdate.Type last(List<TotemUpdate> updates) {
        return updates.get(updates.size() - 1).type();
    }

    @Test
    void theColumnIsItsBlocksFromTheBaseUp() {
        TotemDefinition definition = totem(5, RivalBreak.RESET);
        assertEquals(0, definition.levelOf("world", 10, 64, 10));
        assertEquals(4, definition.levelOf("world", 10, 68, 10));
        assertEquals(-1, definition.levelOf("world", 10, 69, 10), "above the column");
        assertEquals(-1, definition.levelOf("world", 11, 64, 10));
        assertEquals(-1, definition.levelOf("world_nether", 10, 64, 10));
        assertTrue(definition.allowsTool("diamond_sword"));
        assertFalse(definition.allowsTool("DIAMOND_PICKAXE"));
    }

    @Test
    void theColumnMustFitInItsZone() {
        assertThrows(IllegalArgumentException.class, () -> new TotemDefinition("t", "T", ZONE, 10, 78, 10, 5,
                "QUARTZ_BLOCK", "BEDROCK", "BEDROCK", List.of(), true, RivalBreak.RESET, true, List.of(), 0L, List.of()));
    }

    @Test
    void breakingTheWholeColumnWins() {
        TotemRun run = start(totem(5, RivalBreak.RESET));
        for (int level = 0; level < 4; level++) {
            assertEquals(TotemUpdate.Type.BROKEN, last(manager.recordBreak(WIZARDS, level)));
        }
        assertEquals(4, run.brokenCount());
        assertEquals(WIZARDS, run.holder());
        List<TotemUpdate> win = manager.recordBreak(WIZARDS, 4);
        assertEquals(TotemUpdate.Type.WON, last(win));
        assertEquals(WIZARDS, win.get(0).teamId());
        assertTrue(manager.getCurrent().isEmpty());
    }

    @Test
    void aMiniTotemIsAShorterColumn() {
        start(totem(3, RivalBreak.RESET));
        manager.recordBreak(WIZARDS, 2);
        manager.recordBreak(WIZARDS, 0);
        assertEquals(TotemUpdate.Type.WON, last(manager.recordBreak(WIZARDS, 1)), "any order");
    }

    @Test
    void anotherTeamsBreakStartsTheTotemOverAndCountsForNobody() {
        TotemRun run = start(totem(5, RivalBreak.RESET));
        manager.recordBreak(WIZARDS, 0);
        manager.recordBreak(WIZARDS, 1);
        manager.recordBreak(WIZARDS, 2);

        List<TotemUpdate> reset = manager.recordBreak(RAIDERS, 3);

        assertEquals(List.of(TotemUpdate.Type.RESET), reset.stream().map(TotemUpdate::type).toList());
        assertEquals(RAIDERS, reset.get(0).teamId());
        assertEquals(WIZARDS.toString(), reset.get(0).placeholders().get("previous"));
        assertEquals(0, run.brokenCount(), "every block whole again");
        assertNull(run.holder());
        assertEquals(0, manager.activeLevel("world", 10, 64, 10), "the bottom block stands to be broken again");
        assertEquals(1, run.resets());
    }

    @Test
    void withResetAndStartTheRivalsBreakIsItsFirst() {
        TotemRun run = start(totem(5, RivalBreak.RESET_AND_START));
        manager.recordBreak(WIZARDS, 0);
        manager.recordBreak(WIZARDS, 1);

        List<TotemUpdate> updates = manager.recordBreak(RAIDERS, 4);

        assertEquals(List.of(TotemUpdate.Type.RESET, TotemUpdate.Type.BROKEN),
                updates.stream().map(TotemUpdate::type).toList());
        assertEquals(RAIDERS, run.holder());
        assertEquals(1, run.brokenCount());
        assertTrue(run.isBroken(4));
    }

    @Test
    void aBrokenBlockIsNoLongerThereToBreak() {
        start(totem(5, RivalBreak.RESET));
        manager.recordBreak(WIZARDS, 0);
        assertEquals(-1, manager.activeLevel("world", 10, 64, 10));
        assertEquals(List.of(), manager.recordBreak(RAIDERS, 0), "breaking bedrock resets nothing");
    }

    @Test
    void aPlayerWithNoTeamNeverCounts() {
        start(totem(5, RivalBreak.RESET));
        assertEquals(TotemManager.BreakCheck.NO_TEAM, manager.check(null));
        assertEquals(List.of(), manager.recordBreak(null, 0));
        assertEquals(TotemManager.BreakCheck.ALLOWED, manager.check(WIZARDS));
    }

    @Test
    void nothingCountsWithoutARun() {
        assertEquals(TotemManager.BreakCheck.NOT_RUNNING, manager.check(WIZARDS));
        assertEquals(-1, manager.activeLevel("world", 10, 64, 10));
        assertEquals(List.of(), manager.recordBreak(WIZARDS, 0));
    }

    @Test
    void oneTotemAtATime() {
        start(totem(5, RivalBreak.RESET));
        assertTrue(manager.start(totem(3, RivalBreak.RESET)).isEmpty());
        assertTrue(manager.stop().isPresent());
        assertTrue(manager.stop().isEmpty());
    }

    @Test
    void theTimeLimitEndsItWithNoWinner() {
        TotemDefinition limited = new TotemDefinition("totem", "Totem", ZONE, 10, 64, 10, 5, "QUARTZ_BLOCK", "BEDROCK",
                "BEDROCK", List.of(), true, RivalBreak.RESET, true, List.of(), 60L, List.of());
        start(limited);
        manager.recordBreak(WIZARDS, 0);
        now.addAndGet(59_000L);
        assertTrue(manager.tick().isEmpty());
        now.addAndGet(1_000L);
        assertEquals(TotemUpdate.Type.EXPIRED, last(manager.tick()));
        assertTrue(manager.getCurrent().isEmpty());
    }

    @Test
    void aScheduledTimeOpensIt() {
        now.set(java.time.ZonedDateTime.of(2026, 9, 22, 17, 59, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli());
        TotemDefinition scheduled = new TotemDefinition("totem", "Totem", ZONE, 10, 64, 10, 5, "QUARTZ_BLOCK",
                "BEDROCK", "BEDROCK", List.of(), true, RivalBreak.RESET, true, List.of(LocalTime.of(18, 0)), 0L,
                List.of());
        settings = new TotemSettings(true, ZoneOffset.UTC, List.of(scheduled));
        assertTrue(manager.tick().isEmpty(), "the first tick only records when it is");
        now.addAndGet(120_000L);
        assertEquals(TotemUpdate.Type.STARTED, last(manager.tick()));
    }

    @Test
    void whileDisabledNothingOpens() {
        TotemDefinition scheduled = new TotemDefinition("totem", "Totem", ZONE, 10, 64, 10, 5, "QUARTZ_BLOCK",
                "BEDROCK", "BEDROCK", List.of(), true, RivalBreak.RESET, true, List.of(LocalTime.of(18, 0)), 0L,
                List.of());
        settings = new TotemSettings(false, ZoneOffset.UTC, List.of(scheduled));
        now.set(java.time.ZonedDateTime.of(2026, 9, 22, 17, 59, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli());
        manager.tick();
        now.addAndGet(120_000L);
        assertTrue(manager.tick().isEmpty());
    }
}
