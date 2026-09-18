package com.lawkeys.hcfcore.schedule;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Custom timers and tip rotation, without a server. */
class ScheduleTest {

    /**
     * A setting missing from the file takes its built-in default
     * (docs/reference/configuration/index.md), so the defaults must be what schedule.yml ships: an empty prefix or announcement
     * there changed what players saw.
     */
    @Test
    void theBuiltInDefaultsAreTheShippedValues() throws java.io.IOException {
        String shipped;
        try (java.io.InputStream in = ScheduleTest.class.getResourceAsStream("/schedule.yml")) {
            shipped = new String(java.util.Objects.requireNonNull(in).readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
        }
        ScheduleSettings defaults = ScheduleSettings.defaults();
        assertTrue(shipped.contains("prefix: \"" + defaults.tips().prefix() + "\""));
        assertFalse(defaults.tips().messages().isEmpty());
        for (String message : defaults.tips().messages()) {
            assertTrue(shipped.contains("- \"" + message + "\""), message);
        }
        assertTrue(shipped.contains("broadcast: \"" + defaults.keyAll().broadcast() + "\""));
        assertTrue(shipped.contains("label: \"" + defaults.keyAll().label() + "\""));
        assertTrue(shipped.contains("interval-seconds: " + defaults.tips().intervalSeconds()));
    }

    @Nested
    class Timers {

        private final CustomTimers timers = new CustomTimers();

        @Test
        void aTimerEndsAtItsInstant() {
            assertTrue(timers.start("double-points", "&dDouble points", 10_000L));
            assertEquals(10L, timers.get("double-points").orElseThrow().remainingSeconds(0L));
            assertTrue(timers.pollEnded(9_999L).isEmpty());
            assertEquals("double-points", timers.pollEnded(10_000L).get(0).name());
            assertTrue(timers.running().isEmpty(), "an ended timer is taken out");
        }

        /** Players are watching it: a second start must not quietly reset the countdown. */
        @Test
        void aRunningTimerIsNotRestarted() {
            timers.start("keyall", "Key-All", 10_000L);
            assertFalse(timers.start("KeyAll", "Key-All", 99_000L), "names are case-insensitive");
            assertEquals(10_000L, timers.get("keyall").orElseThrow().endsAt());
        }

        @Test
        void theSoonestIsListedFirst() {
            timers.start("later", "Later", 50_000L);
            timers.start("sooner", "Sooner", 20_000L);
            assertEquals(List.of("sooner", "later"),
                    timers.running().stream().map(CustomTimers.Timer::name).toList());
        }

        @Test
        void stoppingATimerMeansItNeverEnds() {
            timers.start("x", "X", 1_000L);
            assertTrue(timers.stop("X").isPresent());
            assertTrue(timers.pollEnded(5_000L).isEmpty());
        }

        @Test
        void aBlankNameIsRefused() {
            assertFalse(timers.start("  ", "Nothing", 1_000L));
        }

        /** "1s" is shown for the last second, never "0" while it still runs. */
        @Test
        void theLastSecondRoundsUp() {
            timers.start("x", "X", 1_500L);
            assertEquals(1L, timers.get("x").orElseThrow().remainingSeconds(1_000L));
        }
    }

    @Nested
    class Tips {

        @Test
        void inOrderTheyCycle() {
            TipRotation rotation = new TipRotation(List.of("a", "b", "c"), false, new Random(1));
            List<String> seen = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                seen.add(rotation.next().orElseThrow());
            }
            assertEquals(List.of("a", "b", "c", "a", "b"), seen);
        }

        /** Otherwise three tips repeat themselves a third of the time. */
        @Test
        void atRandomTheSameTipNeverComesTwiceRunning() {
            TipRotation rotation = new TipRotation(List.of("a", "b", "c"), true, new Random(42));
            String previous = rotation.next().orElseThrow();
            for (int i = 0; i < 500; i++) {
                String current = rotation.next().orElseThrow();
                assertNotEquals(previous, current);
                previous = current;
            }
        }

        @Test
        void atRandomEveryTipStillComesUp() {
            TipRotation rotation = new TipRotation(List.of("a", "b", "c", "d"), true, new Random(7));
            List<String> seen = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                seen.add(rotation.next().orElseThrow());
            }
            assertTrue(seen.containsAll(List.of("a", "b", "c", "d")));
        }

        @Test
        void oneTipAtRandomIsJustThatTip() {
            TipRotation rotation = new TipRotation(List.of("only"), true, new Random(3));
            assertEquals("only", rotation.next().orElseThrow());
            assertEquals("only", rotation.next().orElseThrow());
        }

        @Test
        void noTipsMeansNothing() {
            assertTrue(new TipRotation(List.of(), false, new Random()).next().isEmpty());
        }
    }
}
